package org.jetbrains.jps.incremental.java;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesOnlyRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.javac.*;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.jetbrains.jps.model.java.compiler.*;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

import javax.tools.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.JavaBuilder");
  public static final String BUILDER_NAME = "java";
  private static final String FORMS_BUILDER_NAME = "forms";
  private static final String JAVA_EXTENSION = ".java";
  private static final String FORM_EXTENSION = ".form";
  public static final boolean USE_EMBEDDED_JAVAC = System.getProperty(GlobalOptions.USE_EXTERNAL_JAVAC_OPTION) == null;
  private static final Key<Integer> JAVA_COMPILER_VERSION_KEY = Key.create("_java_compiler_version_");
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-target"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-g", "-deprecation", "-nowarn", "-verbose", "-proc:none", "-proc:only", "-proceedOnError"
  ));

  private static final FileFilter JAVA_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(JAVA_EXTENSION);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), JAVA_EXTENSION);
      }
    };

  private static final FileFilter FORM_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(FORM_EXTENSION);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), FORM_EXTENSION);
      }
    }
    ;

  private static final Key<Callbacks.Backend> DELTA_MAPPINGS_CALLBACK_KEY = Key.create("_dependency_data_");
  private final Executor myTaskRunner;
  private static final List<ClassPostProcessor> ourClassProcessors = new ArrayList<ClassPostProcessor>();

  static {
    registerClassPostProcessor(new ClassPostProcessor() {
      public void process(CompileContext context, OutputFileObject out) {
        final OutputFileObject.Content content = out.getContent();
        final File srcFile = out.getSourceFile();
        boolean isTemp = false;
        final JavaFileObject.Kind outKind = out.getKind();
        if (srcFile != null && content != null) {
          final String outputPath = FileUtil.toSystemIndependentName(out.getFile().getPath());
          final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
          final JavaSourceRootDescriptor rootDescriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, srcFile);
          final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
          if (rootDescriptor != null) {
            isTemp = rootDescriptor.isTemp;
            if (!isTemp) {
              try {
                dataManager.getSourceToOutputMap(rootDescriptor.target).appendOutput(sourcePath, outputPath);
              }
              catch (Exception e) {
                context.processMessage(new CompilerMessage(BUILDER_NAME, e));
              }
            }
          }
          out.setTemp(isTemp);
          if (!isTemp && outKind == JavaFileObject.Kind.CLASS && !Utils.errorsDetected(context)) {
            final Callbacks.Backend callback = DELTA_MAPPINGS_CALLBACK_KEY.get(context);
            if (callback != null) {
              final ClassReader reader = new ClassReader(content.getBuffer(), content.getOffset(), content.getLength());
              callback.associate(outputPath, sourcePath, reader);
            }
          }
        }
        if (!isTemp && outKind != JavaFileObject.Kind.CLASS && outKind != JavaFileObject.Kind.SOURCE) { // this should be a generated resource
          try {
            FSOperations.markDirty(context, out.getFile());
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    });
  }

  public static void registerClassPostProcessor(ClassPostProcessor processor) {
    ourClassProcessors.add(processor);
  }

  public JavaBuilder(Executor tasksExecutor) {
    super(BuilderCategory.TRANSLATOR);
    myTaskRunner = new SequentialTaskExecutor(tasksExecutor);
    //add here class processors in the sequence they should be executed
  }

  @NotNull
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  public ExitCode build(final CompileContext context,
                        final ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException {
    try {
      final Set<File> filesToCompile = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      final Set<File> formsToCompile = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

      dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
        public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
          if (descriptor instanceof ResourcesOnlyRootDescriptor) {
            return true;
          }
          if (JAVA_SOURCES_FILTER.accept(file)) {
            filesToCompile.add(file);
          }
          else if (FORM_SOURCES_FILTER.accept(file)) {
            formsToCompile.add(file);
          }
          return true;
        }
      });

      // force compilation of bound source file if the form is dirty
      final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
        context.getProjectDescriptor().getProject()).getCompilerExcludes();
      if (!context.isProjectRebuild()) {
        for (Iterator<File> formsIterator = formsToCompile.iterator(); formsIterator.hasNext(); ) {
          final File form = formsIterator.next();
          final JavaSourceRootDescriptor descriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, form);
          if (descriptor == null) {
            continue;
          }
          for (JavaSourceRootDescriptor rd : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(descriptor.target, context)) {
            final File boundSource = getBoundSource(rd.root, form);
            if (boundSource == null) {
              continue;
            }
            if (!excludes.isExcluded(boundSource)) {
              filesToCompile.add(boundSource);
            }
            else {
              formsIterator.remove();
            }
            break;
          }
        }

        // form should be considered dirty if the class it is bound to is dirty
        final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
        for (File srcFile : filesToCompile) {
          final String srcPath = srcFile.getPath();
          final Collection<String> boundForms = sourceToFormMap.getState(srcPath);
          if (boundForms != null) {
            for (String formPath : boundForms) {
              final File formFile = new File(formPath);
              if (!excludes.isExcluded(formFile)) {
                if (formFile.exists()) {
                  FSOperations.markDirty(context, formFile);
                  formsToCompile.add(formFile);
                }
              }
            }
            sourceToFormMap.remove(srcPath);
          }
        }
      }

      final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled() && context.isMake()) {
        if (filesToCompile.size() > 0) {
          logger.logCompiledFiles(filesToCompile, BUILDER_NAME, "Compiling files:");
        }
        if (!formsToCompile.isEmpty()) {
          logger.logCompiledFiles(formsToCompile, FORMS_BUILDER_NAME, "Compiling forms:");
        }
      }

      return compile(context, chunk, dirtyFilesHolder, filesToCompile, formsToCompile);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (message == null) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        message = "Internal error: \n" + out.toString();
      }
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      throw new ProjectBuildException(message, e);
    }
  }

  @Override
  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return JAVA_SOURCES_FILTER.accept(file) || FORM_SOURCES_FILTER.accept(file);
  }

  @Nullable
  private static File getBoundSource(File srcRoot, File formFile) throws IOException {
    final String boundClassName = FormsParsing.readBoundClassName(formFile);
    if (boundClassName == null) {
      return null;
    }
    String relPath = boundClassName.replace('.', '/') + JAVA_EXTENSION;
    while (true) {
      final File candidate = new File(srcRoot, relPath);
      if (candidate.exists()) {
        return candidate.isFile() ? candidate : null;
      }
      final int index = relPath.lastIndexOf('/');
      if (index <= 0) {
        return null;
      }
      relPath = relPath.substring(0, index) + JAVA_EXTENSION;
    }
  }

  private ExitCode compile(final CompileContext context,
                           ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                           Collection<File> files,
                           Collection<File> forms)
    throws Exception {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final boolean hasSourcesToCompile = !files.isEmpty() || !forms.isEmpty();

    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return exitCode;
    }

    final ProjectPaths paths = context.getProjectPaths();
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final boolean addNotNullAssertions = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      pd.getProject()).isAddNotNullAssertions();

    final Collection<File> classpath =
      paths.getCompilationClasspath(chunk, false/*context.isProjectRebuild()*/);
    final Collection<File> platformCp =
      paths.getPlatformCompilationClasspath(chunk, false/*context.isProjectRebuild()*/);

    // begin compilation round
    final DiagnosticSink diagnosticSink = new DiagnosticSink(context);
    final OutputFilesSink outputSink = new OutputFilesSink(context);
    final Mappings delta = pd.dataManager.getMappings().createDelta();
    DELTA_MAPPINGS_CALLBACK_KEY.set(context, delta.getCallback());
    try {
      if (hasSourcesToCompile) {
        exitCode = ExitCode.OK;
        final Set<File> srcPath = new HashSet<File>();
        final BuildRootIndex index = pd.getBuildRootIndex();
        for (ModuleBuildTarget target : chunk.getTargets()) {
          for (JavaSourceRootDescriptor rd : index.getTempTargetRoots(target, context)) {
            srcPath.add(rd.root);
          }
        }

        final String chunkName = getChunkPresentableName(chunk);
        context.processMessage(new ProgressMessage("Compiling java [" + chunkName + "]"));

        final int filesCount = files.size();
        boolean compiledOk = true;
        if (filesCount > 0) {
          LOG.info("Compiling " + filesCount + " java files; module: " + chunkName + (chunk.containsTests() ? " (tests)" : ""));
          if (LOG.isDebugEnabled()) {
            for (File file : files) {
              LOG.debug("Compiling " + file.getPath());
            }
            LOG.debug(" classpath for " + chunkName + ":");
            for (File file : classpath) {
              LOG.debug("  " + file.getAbsolutePath());
            }
            LOG.debug(" platform classpath for " + chunkName + ":");
            for (File file : platformCp) {
              LOG.debug("  " + file.getAbsolutePath());
            }
          }
          compiledOk = compileJava(context, chunk, files, classpath, platformCp, srcPath, diagnosticSink, outputSink);
        }

        context.checkCanceled();

        if (diagnosticSink.getErrorCount() == 0 && (!forms.isEmpty() || addNotNullAssertions)) {
          final Map<File, String> chunkSourcePath = ProjectPaths.getSourceRootsWithDependents(chunk);
          final InstrumentationClassFinder finder = createInstrumentationClassFinder(platformCp, classpath, chunkSourcePath, outputSink);

          try {
            if (!forms.isEmpty()) {
              try {
                context.processMessage(new ProgressMessage("Instrumenting forms [" + chunkName + "]"));
                instrumentForms(context, chunk, chunkSourcePath, finder, forms, outputSink);
                JpsUiDesignerConfiguration configuration = JpsUiDesignerExtensionService.getInstance().getUiDesignerConfiguration(pd.getProject());
                if (configuration != null && configuration.isCopyFormsRuntimeToOutput()) {
                  for (ModuleBuildTarget target : chunk.getTargets()) {
                    if (!target.isTests()) {
                      final File outputDir = target.getOutputDir();
                      if (outputDir != null) {
                        CopyResourcesUtil.copyFormsRuntime(outputDir.getAbsolutePath(), false);
                      }
                    }
                  }
                }
              }
              finally {
                context.processMessage(new ProgressMessage("Finished instrumenting forms [" + chunkName + "]"));
              }
            }

            context.checkCanceled();

            if (addNotNullAssertions) {
              try {
                context.processMessage(new ProgressMessage("Adding NotNull assertions [" + chunkName + "]"));
                instrumentNotNull(context, outputSink, finder);
              }
              finally {
                context.processMessage(new ProgressMessage("Finished adding NotNull assertions [" + chunkName + "]"));
              }
            }
          }
          finally {
            finder.releaseResources();
          }
        }

        context.checkCanceled();

        if (!compiledOk && diagnosticSink.getErrorCount() == 0) {
          diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Compilation failed: internal java compiler error"));
        }
        if (!Utils.PROCEED_ON_ERROR_KEY.get(context, Boolean.FALSE) && diagnosticSink.getErrorCount() > 0) {
          if (!compiledOk) {
            diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.OTHER, "Errors occurred while compiling module '" + chunkName + "'"));
          }
          throw new ProjectBuildException(
            "Compilation failed: errors: " + diagnosticSink.getErrorCount() + "; warnings: " + diagnosticSink.getWarningCount()
          );
        }
      }
    }
    finally {
      outputSink.writePendingData();

      final Set<File> successfullyCompiled = outputSink.getSuccessfullyCompiled();
      DELTA_MAPPINGS_CALLBACK_KEY.set(context, null);

      if (JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, files, successfullyCompiled)) {
        exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
    }

    return exitCode;
  }

  private static String getChunkPresentableName(ModuleChunk chunk) {
    final Set<JpsModule> modules = chunk.getModules();
    if (modules.isEmpty()) {
      return "<empty>";
    }
    if (modules.size() == 1) {
      return modules.iterator().next().getName();
    }
    final StringBuilder buf = new StringBuilder();
    for (JpsModule module : modules) {
      if (buf.length() > 0) {
        buf.append(",");
      }
      buf.append(module.getName());
    }
    return buf.toString();
  }

  private boolean compileJava(
    final CompileContext context,
    ModuleChunk chunk,
    Collection<File> files,
    Collection<File> classpath,
    Collection<File> platformCp,
    Collection<File> sourcePath,
    DiagnosticOutputConsumer diagnosticSink,
    final OutputFileConsumer outputSink) throws Exception {

    final TasksCounter counter = new TasksCounter();
    COUNTER_KEY.set(context, counter);

    final Set<JpsModule> modules = chunk.getModules();
    ProcessorConfigProfile profile = null;
    if (modules.size() == 1) {
      profile = context.getAnnotationProcessingProfile(modules.iterator().next());
    }
    else {
      // check that all chunk modules are excluded from annotation processing
      for (JpsModule module : modules) {
        final ProcessorConfigProfile prof = context.getAnnotationProcessingProfile(module);
        if (prof.isEnabled()) {
          String message = "Annotation processing is not supported for module cycles. Please ensure that all modules from cycle [" + getChunkPresentableName(chunk) + "] are excluded from annotation processing";
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
          return true;
        }
      }
    }

    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk, profile);
    final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
    try {
      final boolean rc;
      if (USE_EMBEDDED_JAVAC) {
        final boolean useEclipse = useEclipseCompiler(context);
        rc = JavacMain.compile(
          options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, classesConsumer, context.getCancelStatus(), useEclipse
        );
      }
      else {
        final JavacServerClient client = ensureJavacServerLaunched(context);
        final RequestFuture<JavacServerResponseHandler> future = client.sendCompileRequest(
          options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, classesConsumer
        );
        while (!future.waitFor(100L, TimeUnit.MILLISECONDS)) {
          if (context.getCancelStatus().isCanceled()) {
            future.cancel(false);
          }
        }
        rc = future.getMessageHandler().isTerminatedSuccessfully();
      }
      return rc;
    }
    finally {
      counter.await();
    }
  }

  private static boolean useEclipseCompiler(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    return USE_EMBEDDED_JAVAC && "Eclipse".equalsIgnoreCase(JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getJavaCompilerId());
  }

  private void submitAsyncTask(CompileContext context, final Runnable taskRunnable) {
    final TasksCounter counter = COUNTER_KEY.get(context);

    assert counter != null;

    counter.incTaskCount();
    myTaskRunner.execute(new Runnable() {
      public void run() {
        try {
          taskRunnable.run();
        }
        finally {
          counter.decTaskCounter();
        }
      }
    });
  }

  private static synchronized JavacServerClient ensureJavacServerLaunched(CompileContext context) throws Exception {
    final ExternalJavacDescriptor descriptor = ExternalJavacDescriptor.KEY.get(context);
    if (descriptor != null) {
      return descriptor.client;
    }
    // start server here
    final String hostString = System.getProperty(GlobalOptions.HOSTNAME_OPTION, "localhost");
    final int port = findFreePort();
    final int heapSize = getJavacServerHeapSize(context);

    // defaulting to the same jdk that used to run the build process
    String javaHome = SystemProperties.getJavaHome();
    int javaVersion = convertToNumber(SystemProperties.getJavaVersion());

    for (JpsSdk<?> sdk : context.getProjectDescriptor().getProjectJavaSdks()) {
      final String version = sdk.getVersionString();
      final int ver = convertToNumber(version);
      if (ver > javaVersion) {
        javaVersion = ver;
        javaHome = sdk.getHomePath();
      }
    }

    final BaseOSProcessHandler processHandler = JavacServerBootstrap.launchJavacServer(
      javaHome, heapSize, port, Utils.getSystemRoot(), getCompilationVMOptions(context)
    );
    final JavacServerClient client = new JavacServerClient();
    try {
      client.connect(hostString, port);
    }
    catch (Throwable ex) {
      processHandler.destroyProcess();
      throw new Exception("Failed to connect to external javac process: ", ex);
    }
    ExternalJavacDescriptor.KEY.set(context, new ExternalJavacDescriptor(processHandler, client));
    return client;
  }

  private static int convertToNumber(String ver) {
    if (ver == null) {
      return 0;
    }
    final int quoteBegin = ver.indexOf("\"");
    if (quoteBegin >= 0) {
      final int quoteEnd = ver.indexOf("\"", quoteBegin + 1);
      if (quoteEnd > quoteBegin) {
        ver = ver.substring(quoteBegin + 1, quoteEnd);
      }
    }
    if (ver.isEmpty()) {
      return 0;
    }

    final String prefix = "1.";
    final int parseBegin = ver.startsWith(prefix)? prefix.length() : 0;

    final int parseEnd = ver.indexOf(".", parseBegin);
    if (parseEnd > 0) {
      ver = ver.substring(parseBegin, parseEnd);
    }
    else {
      ver = ver.substring(parseBegin);
    }

    try {
      return Integer.parseInt(ver);
    }
    catch (NumberFormatException ignored) {
    }
    return 0;
  }

  private static int findFreePort() {
    try {
      final ServerSocket serverSocket = new ServerSocket(0);
      try {
        return serverSocket.getLocalPort();
      }
      finally {
        //workaround for linux : calling close() immediately after opening socket
        //may result that socket is not closed
        synchronized (serverSocket) {
          try {
            serverSocket.wait(1);
          }
          catch (Throwable ignored) {
          }
        }
        serverSocket.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace(System.err);
      return JavacServer.DEFAULT_SERVER_PORT;
    }
  }

  private static int getJavacServerHeapSize(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final JpsJavaCompilerOptions options = config.getCurrentCompilerOptions();
    return options.MAXIMUM_HEAP_SIZE;
  }

  private static InstrumentationClassFinder createInstrumentationClassFinder(Collection<File> platformCp,
                                                                             Collection<File> classpath,
                                                                             Map<File, String> chunkSourcePath, final OutputFilesSink outputSink) throws MalformedURLException {
    final URL[] platformUrls = new URL[platformCp.size()];
    int index = 0;
    for (File file : platformCp) {
      platformUrls[index++] = file.toURI().toURL();
    }
    
    final List<URL> urls = new ArrayList<URL>(classpath.size() + chunkSourcePath.size() + 1);
    for (File file : classpath) {
      urls.add(file.toURI().toURL());
    }
    urls.add(getResourcePath(GridConstraints.class).toURI().toURL()); // forms_rt.jar
    //urls.add(getResourcePath(CellConstraints.class).toURI().toURL());  // jgoodies-forms
    for (File file : chunkSourcePath.keySet()) { // sourcepath for loading forms resources
      urls.add(file.toURI().toURL());
    }

    return new InstrumentationClassFinder(platformUrls, urls.toArray(new URL[urls.size()])) {
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final OutputFileObject.Content content = outputSink.lookupClassBytes(internalClassName.replace("/", "."));
        if (content != null) {
          return new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
        }
        return null;
      }
    };
  }

  private static final Key<List<String>> JAVAC_OPTIONS = Key.create("_javac_options_");
  private static final Key<List<String>> JAVAC_VM_OPTIONS = Key.create("_javac_vm_options_");

  private static List<String> getCompilationVMOptions(CompileContext context) {
    List<String> cached = JAVAC_VM_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context);
      cached = JAVAC_VM_OPTIONS.get(context);
    }
    return cached;
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk, ProcessorConfigProfile profile) {
    List<String> cached = JAVAC_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context);
      cached = JAVAC_OPTIONS.get(context);
    }

    final List<String> options = new ArrayList<String>(cached);
    if (!isEncodingSet(options)) {
      final CompilerEncodingConfiguration config = context.getProjectDescriptor().getEncodingConfiguration();
      final String encoding = config.getPreferredModuleChunkEncoding(chunk);
      if (config.getAllModuleChunkEncodings(chunk).size() > 1) {
        final StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("Multiple encodings set for module chunk ").append(getChunkPresentableName(chunk));
        if (encoding != null) {
          msgBuilder.append("\n\"").append(encoding).append("\" will be used by compiler");
        }
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, msgBuilder.toString()));
      }
      if (!StringUtil.isEmpty(encoding)) {
        options.add("-encoding");
        options.add(encoding);
      }
    }

    final String langLevel = getLanguageLevel(chunk.getModules().iterator().next());
    if (!StringUtil.isEmpty(langLevel)) {
      options.add("-source");
      options.add(langLevel);
    }

    JpsJavaCompilerConfiguration compilerConfiguration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      context.getProjectDescriptor().getProject());
    String bytecodeTarget = null;
    int chunkSdkVersion = -1;
    for (JpsModule module : chunk.getModules()) {
      final JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null) {
        final int moduleSdkVersion = convertToNumber(sdk.getVersionString());
        if (moduleSdkVersion != 0 /*could determine the version*/&& (chunkSdkVersion < 0 || chunkSdkVersion > moduleSdkVersion)) {
          chunkSdkVersion = moduleSdkVersion;
        }
      }

      final String moduleTarget = compilerConfiguration.getByteCodeTargetLevel(module.getName());
      if (moduleTarget == null) {
        continue;
      }
      if (bytecodeTarget == null) {
        bytecodeTarget = moduleTarget;
      }
      else {
        if (moduleTarget.compareTo(bytecodeTarget) < 0) {
          bytecodeTarget = moduleTarget; // use the lower possible target among modules that form the chunk
        }
      }
    }
    if (bytecodeTarget != null) {
      options.add("-target");
      options.add(bytecodeTarget);
    }
    else {
      if (chunkSdkVersion > 0 && getCompilerSdkVersion(context) > chunkSdkVersion) {
        // force lower bytecode target level to match the version of sdk assigned to this chunk
        options.add("-target");
        options.add("1." + chunkSdkVersion);
      }
    }

    if (profile != null && profile.isEnabled()) {
      // configuring annotation processing
      if (!profile.isObtainProcessorsFromClasspath()) {
        final String processorsPath = profile.getProcessorPath();
        options.add("-processorpath");
        options.add(processorsPath == null? "" : FileUtil.toSystemDependentName(processorsPath.trim()));
      }

      for (String procFQName : profile.getProcessors()) {
        options.add("-processor");
        options.add(procFQName);
      }

      for (Map.Entry<String, String> optionEntry : profile.getProcessorOptions().entrySet()) {
        options.add("-A" + optionEntry.getKey() + "=" + optionEntry.getValue());
      }

      final File srcOutput = context.getProjectPaths().getAnnotationProcessorGeneratedSourcesOutputDir(
        chunk.getModules().iterator().next(), chunk.containsTests(), profile
      );
      if (srcOutput != null) {
        srcOutput.mkdirs();
        options.add("-s");
        options.add(srcOutput.getPath());
      }
    }
    else {
      options.add("-proc:none");
    }

    return options;
  }

  private static String getLanguageLevel(JpsModule module) {
    LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    if (level == null) return null;
    switch (level) {
      case JDK_1_3: return "1.3";
      case JDK_1_4: return "1.4";
      case JDK_1_5: return "1.5";
      case JDK_1_6: return "1.6";
      case JDK_1_7: return "1.7";
      case JDK_1_8: return "8";
      default: return null;
    }
  }

  private static boolean isEncodingSet(List<String> options) {
    for (String option : options) {
      if ("-encoding".equals(option)) {
        return true;
      }
    }
    return false;
  }


  private static int getCompilerSdkVersion(CompileContext context) {
    final Integer cached = JAVA_COMPILER_VERSION_KEY.get(context);
    if (cached != null) {
      return cached;
    }
    int javaVersion = convertToNumber(SystemProperties.getJavaVersion());
    if (!USE_EMBEDDED_JAVAC) {
      // in case of external javac, run compiler from the newest jdk that is used in the project
      for (JpsSdk<?> sdk : context.getProjectDescriptor().getProjectJavaSdks()) {
        final String version = sdk.getVersionString();
        final int ver = convertToNumber(version);
        if (ver > javaVersion) {
          javaVersion = ver;
        }
      }
    }
    JAVA_COMPILER_VERSION_KEY.set(context, javaVersion);
    return javaVersion;
  }

  private static void loadCommonJavacOptions(CompileContext context) {
    final List<String> options = new ArrayList<String>();
    final List<String> vmOptions = new ArrayList<String>();

    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final boolean useEclipseCompiler = useEclipseCompiler(context);
    final JpsJavaCompilerOptions compilerOptions = compilerConfig.getCurrentCompilerOptions();
    if (compilerOptions.DEBUGGING_INFO) {
      options.add("-g");
    }
    if (compilerOptions.DEPRECATION) {
      options.add("-deprecation");
    }
    if (compilerOptions.GENERATE_NO_WARNINGS) {
      options.add("-nowarn");
    }
    if (compilerOptions instanceof EclipseCompilerOptions) {
      final EclipseCompilerOptions eclipseOptions = (EclipseCompilerOptions)compilerOptions;
      if (eclipseOptions.PROCEED_ON_ERROR) {
        options.add("-proceedOnError");
      }
    }
    final String customArgs = compilerOptions.ADDITIONAL_OPTIONS_STRING;
    if (customArgs != null) {
      final StringTokenizer customOptsTokenizer = new StringTokenizer(customArgs, " \t\r\n");
      boolean skip = false;
      while (customOptsTokenizer.hasMoreTokens()) {
        final String userOption = customOptsTokenizer.nextToken();
        if (FILTERED_OPTIONS.contains(userOption)) {
          skip = true;
          continue;
        }
        if (!skip) {
          if (!FILTERED_SINGLE_OPTIONS.contains(userOption)) {
            if (userOption.startsWith("-J-")) {
              vmOptions.add(userOption.substring("-J".length()));
            }
            else {
              options.add(userOption);
            }
          }
        }
      }
    }

    if (useEclipseCompiler) {
      for (String option : options) {
        if (option.startsWith("-proceedOnError")) {
          Utils.PROCEED_ON_ERROR_KEY.set(context, Boolean.TRUE);
          break;
        }
      }
    }

    JAVAC_OPTIONS.set(context, options);
    JAVAC_VM_OPTIONS.set(context, vmOptions);
  }

  @Override
  public void cleanupChunkResources(CompileContext context) {
    JavaBuilderUtil.cleanupChunkResources(context);
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new LinkedHashMap<File, Set<File>>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      final File outputDir = target.getOutputDir();
      if (outputDir == null) {
        continue;
      }
      final Set<File> roots = new LinkedHashSet<File>();
      for (JavaSourceRootDescriptor descriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
        roots.add(descriptor.root);
      }
      map.put(outputDir, roots);
    }
    return map;
  }

  // todo: probably instrument other NotNull-like annotations defined in project settings?
  private static void instrumentNotNull(CompileContext context, OutputFilesSink sink, final InstrumentationClassFinder finder) {
    for (final OutputFileObject fileObject : sink.getFileObjects()) {
      final OutputFileObject.Content originalContent = fileObject.getContent();
      if (originalContent == null || fileObject.getKind() != JavaFileObject.Kind.CLASS) {
        continue;
      }
      final ClassReader reader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
      final int version = getClassFileVersion(reader);
      if (version >= Opcodes.V1_5) {
        boolean success = false;
        final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
        try {
          final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
          reader.accept(instrumenter, 0);
          if (instrumenter.isModification()) {
            fileObject.updateContent(writer.toByteArray());
          }
          success = true;
        }
        catch (Throwable e) {
          final StringBuilder msg = new StringBuilder();
          msg.append("@NotNull instrumentation failed ");
          final File sourceFile = fileObject.getSourceFile();
          if (sourceFile != null) {
            msg.append(" for ").append(sourceFile.getName());
          }
          msg.append(": ").append(e.getMessage());
          context.processMessage(
            new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, msg.toString(), sourceFile != null ? sourceFile.getPath() : null));
        }
        finally {
          if (!success) {
            sink.markError(fileObject);
          }
        }
      }
    }
  }

  private static void instrumentForms(CompileContext context,
                                      ModuleChunk chunk,
                                      final Map<File, String> chunkSourcePath,
                                      final InstrumentationClassFinder finder, Collection<File> formsToInstrument,
                                      OutputFilesSink outputSink) throws ProjectBuildException {

    final Map<String, File> class2form = new HashMap<String, File>();
    final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
    final Set<File> touchedFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

    final Map<String, OutputFileObject> compiledClassNames = new HashMap<String, OutputFileObject>();
    for (OutputFileObject fileObject : outputSink.getFileObjects()) {
      if (fileObject.getKind() == JavaFileObject.Kind.CLASS) {
        compiledClassNames.put(fileObject.getClassName(), fileObject);
      }
    }

    final MyNestedFormLoader nestedFormsLoader =
      new MyNestedFormLoader(chunkSourcePath, ProjectPaths.getOutputPathsWithDependents(chunk));

    for (File formFile : formsToInstrument) {
      final LwRootContainer rootContainer;
      try {
        rootContainer = com.intellij.uiDesigner.compiler.Utils.getRootContainer(
          formFile.toURI().toURL(), new CompiledClassPropertiesProvider( finder.getLoader())
        );
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
        continue;
      }
      catch (UnexpectedFormElementException e) {
        context.processMessage(new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage(), formFile.getPath()));
        LOG.info(e);
        continue;
      }
      catch (UIDesignerException e) {
        context.processMessage(new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage(), formFile.getPath()));
        LOG.info(e);
        continue;
      }
      catch (Exception e) {
        throw new ProjectBuildException("Cannot process form file " + formFile.getAbsolutePath(), e);
      }

      final String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        continue;
      }

      final OutputFileObject outputClassFile = findClassFile(compiledClassNames, classToBind);
      if (outputClassFile == null) {
        context.processMessage(new CompilerMessage(
          BUILDER_NAME, BuildMessage.Kind.WARNING, "Class to bind does not exist: " + classToBind, formFile.getAbsolutePath())
        );
        continue;
      }

      final File alreadyProcessedForm = class2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        context.processMessage(
          new CompilerMessage(
            FORMS_BUILDER_NAME, BuildMessage.Kind.WARNING,
            formFile.getAbsolutePath() + ": The form is bound to the class " + classToBind + ".\nAnother form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class",
            formFile.getAbsolutePath())
        );
        continue;
      }

      class2form.put(classToBind, formFile);

      boolean success = true;
      try {
        final OutputFileObject.Content originalContent = outputClassFile.getContent();
        final ClassReader classReader =
          new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());

        final int version = getClassFileVersion(classReader);
        final InstrumenterClassWriter classWriter = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, nestedFormsLoader, false, classWriter);
        final byte[] patchedBytes = codeGenerator.patchClass(classReader);
        if (patchedBytes != null) {
          outputClassFile.updateContent(patchedBytes);
        }

        final FormErrorInfo[] warnings = codeGenerator.getWarnings();
        for (final FormErrorInfo warning : warnings) {
          context.processMessage(
            new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.WARNING, warning.getErrorMessage(), formFile.getAbsolutePath())
          );
        }

        final FormErrorInfo[] errors = codeGenerator.getErrors();
        if (errors.length > 0) {
          success = false;
          StringBuilder message = new StringBuilder();
          for (final FormErrorInfo error : errors) {
            if (message.length() > 0) {
              message.append("\n");
            }
            message.append(formFile.getAbsolutePath()).append(": ").append(error.getErrorMessage());
          }
          context.processMessage(new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.ERROR, message.toString()));
        }
        else {
          final File sourceFile = outputClassFile.getSourceFile();
          if (sourceFile != null) {
            if (touchedFiles.add(sourceFile)) { // clear data once before updating
              sourceToFormMap.update(sourceFile.getPath(), formFile.getPath());
            }
            else {
              sourceToFormMap.appendData(sourceFile.getPath(), formFile.getPath());
            }
          }
        }
      }
      catch (Exception e) {
        success = false;
        context.processMessage(new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.ERROR, "Forms instrumentation failed" + e.getMessage(), formFile.getAbsolutePath()));
      }
      finally {
        if (!success) {
          outputSink.markError(outputClassFile);
        }
      }
    }
  }

  private static OutputFileObject findClassFile(Map<String, OutputFileObject> outputs, String classToBind) {
    while (true) {
      final OutputFileObject fo = outputs.get(classToBind);
      if (fo != null) {
        return fo;
      }
      final int dotIndex = classToBind.lastIndexOf('.');
      if (dotIndex <= 0) {
        return null;
      }
      classToBind = classToBind.substring(0, dotIndex) + "$" + classToBind.substring(dotIndex + 1);
    }
  }

  private static int getClassFileVersion(ClassReader reader) {
    final Ref<Integer> result = new Ref<Integer>(0);
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, 0);
    return result.get();
  }

  private static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }

  private class DiagnosticSink implements DiagnosticOutputConsumer {
    private final CompileContext myContext;
    private volatile int myErrorCount = 0;
    private volatile int myWarningCount = 0;

    public DiagnosticSink(CompileContext context) {
      myContext = context;
    }

    public void registerImports(final String className, final Collection<String> imports, final Collection<String> staticImports) {
      submitAsyncTask(myContext, new Runnable() {
        public void run() {
          final Callbacks.Backend callback = DELTA_MAPPINGS_CALLBACK_KEY.get(myContext);
          if (callback != null) {
            callback.registerImports(className, imports, staticImports);
          }
        }
      });
    }

    public void outputLineAvailable(String line) {
      if (!StringUtil.isEmpty(line)) {
        if (line.contains("java.lang.OutOfMemoryError")) {
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "OutOfMemoryError: insufficient memory"));
          myErrorCount++;
        }
        else {
          final BuildMessage.Kind kind = getKindByMessageText(line);
          if (kind == BuildMessage.Kind.ERROR) {
            myErrorCount++;
          }
          else if (kind == BuildMessage.Kind.WARNING) {
            myWarningCount++;
          }
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, kind, line));
        }
      }
    }

    private BuildMessage.Kind getKindByMessageText(String line) {
      final String lowercasedLine = line.toLowerCase(Locale.US);
      if (lowercasedLine.contains("error") || lowercasedLine.contains("requires target release")) {
        return BuildMessage.Kind.ERROR;
      }
      return BuildMessage.Kind.INFO;
    }

    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      final CompilerMessage.Kind kind;
      switch (diagnostic.getKind()) {
        case ERROR:
          kind = BuildMessage.Kind.ERROR;
          myErrorCount++;
          break;
        case MANDATORY_WARNING:
        case WARNING:
        case NOTE:
          kind = BuildMessage.Kind.WARNING;
          myWarningCount++;
          break;
        default:
          kind = BuildMessage.Kind.INFO;
      }
      final JavaFileObject source = diagnostic.getSource();
      File sourceFile = null;
      try {
        sourceFile = source != null ? Utils.convertToFile(source.toUri()) : null;
      }
      catch (Exception e) {
        LOG.info(e);
      }
      final String srcPath = sourceFile != null ? FileUtil.toSystemIndependentName(sourceFile.getPath()) : null;
      String message = diagnostic.getMessage(Locale.US);
      if (Utils.IS_TEST_MODE) {
        LOG.info(message);
      }
      myContext.processMessage(
        new CompilerMessage(BUILDER_NAME, kind, message, srcPath, diagnostic.getStartPosition(),
                            diagnostic.getEndPosition(), diagnostic.getPosition(), diagnostic.getLineNumber(),
                            diagnostic.getColumnNumber()));
    }

    public int getErrorCount() {
      return myErrorCount;
    }

    public int getWarningCount() {
      return myWarningCount;
    }
  }

  private static class MyNestedFormLoader implements NestedFormLoader {
    private final Map<File, String> mySourceRoots;
    private final Collection<File> myOutputRoots;
    private final HashMap<String, LwRootContainer> myCache = new HashMap<String, LwRootContainer>();

    /**
     * @param sourceRoots all source roots for current module chunk and all dependent recursively
     * @param outputRoots output roots for this module chunk and all dependent recursively
     */
    public MyNestedFormLoader(Map<File, String> sourceRoots, Collection<File> outputRoots) {
      mySourceRoots = sourceRoots;
      myOutputRoots = outputRoots;
    }

    public LwRootContainer loadForm(String formFileName) throws Exception {
      if (myCache.containsKey(formFileName)) {
        return myCache.get(formFileName);
      }

      final String relPath = FileUtil.toSystemIndependentName(formFileName);

      for (Map.Entry<File, String> entry : mySourceRoots.entrySet()) {
        final File sourceRoot = entry.getKey();
        final String prefix = entry.getValue();
        String path = relPath;
        if (prefix != null && FileUtil.startsWith(path, prefix)) {
          path = path.substring(prefix.length());
        }
        final File formFile = new File(sourceRoot, path);
        if (formFile.exists()) {
          final BufferedInputStream stream = new BufferedInputStream(new FileInputStream(formFile));
          try {
            return loadForm(formFileName, stream);
          }
          finally {
            stream.close();
          }
        }
      }

      throw new Exception("Cannot find nested form file " + formFileName);
    }

    private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
      final LwRootContainer container = com.intellij.uiDesigner.compiler.Utils.getRootContainer(resourceStream, null);
      myCache.put(formFileName, container);
      return container;
    }

    public String getClassToBindName(LwRootContainer container) {
      final String className = container.getClassToBind();
      for (File outputRoot : myOutputRoots) {
        final String result = getJVMClassName(outputRoot, className.replace('.', '/'));
        if (result != null) {
          return result.replace('/', '.');
        }
      }
      return className;
    }
  }

  @Nullable
  private static String getJVMClassName(File outputRoot, String className) {
    while (true) {
      final File candidateClass = new File(outputRoot, className + ".class");
      if (candidateClass.exists()) {
        return className;
      }
      final int position = className.lastIndexOf('/');
      if (position < 0) {
        return null;
      }
      className = className.substring(0, position) + '$' + className.substring(position + 1);
    }
  }

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

  private class ClassProcessingConsumer implements OutputFileConsumer {
    private final CompileContext myContext;
    private final OutputFileConsumer myDelegateOutputFileSink;

    public ClassProcessingConsumer(CompileContext context, OutputFileConsumer sink) {
      myContext = context;
      myDelegateOutputFileSink = sink != null ? sink : new OutputFileConsumer() {
        public void save(@NotNull OutputFileObject fileObject) {
          throw new RuntimeException("Output sink for compiler was not specified");
        }
      };
    }

    public void save(@NotNull final OutputFileObject fileObject) {
      submitAsyncTask(myContext, new Runnable() {
        public void run() {
          try {
            for (ClassPostProcessor processor : ourClassProcessors) {
              processor.process(myContext, fileObject);
            }
          }
          finally {
            myDelegateOutputFileSink.save(fileObject);
          }
        }
      });
    }
  }


  private static final Key<TasksCounter> COUNTER_KEY = Key.create("_async_task_counter_");

  private static final class TasksCounter {
    private int myCounter = 0;

    public synchronized void incTaskCount() {
      myCounter++;
    }

    public synchronized void decTaskCounter() {
      myCounter = Math.max(0, myCounter - 1);
      if (myCounter == 0) {
        notifyAll();
      }
    }

    public synchronized void await() {
      while (myCounter > 0) {
        try {
          wait();
        }
        catch (InterruptedException e) {
        }
      }
    }
  }
}
