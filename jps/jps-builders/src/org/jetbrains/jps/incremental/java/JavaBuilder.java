package org.jetbrains.jps.incremental.java;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToFormMapping;
import org.jetbrains.jps.javac.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_NAME = "java";
  private static final String FORMS_BUILDER_NAME = "forms";
  private static final String JAVA_EXTENSION = ".java";
  private static final String FORM_EXTENSION = ".form";
  public static final boolean USE_EMBEDDED_JAVAC = System.getProperty(GlobalOptions.USE_EXTERNAL_JAVAC_OPTION) == null;

  public static final FileFilter JAVA_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getPath().endsWith(JAVA_EXTENSION);
    }
  };
  private static final FileFilter FORM_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getPath().endsWith(FORM_EXTENSION);
    }
  };

  private static final Key<Callbacks.Backend> DELTA_MAPPINGS_CALLBACK_KEY = Key.create("_dependency_data_");
  private final ExecutorService myTaskRunner;
  private int myTasksInProgress = 0;
  private final Object myCounterLock = new Object();
  private final List<ClassPostProcessor> myClassProcessors = new ArrayList<ClassPostProcessor>();

  public JavaBuilder(ExecutorService tasksExecutor) {
    super(BuilderCategory.TRANSLATOR);
    myTaskRunner = tasksExecutor;
    //add here class processors in the sequence they should be executed
    myClassProcessors.add(new ClassPostProcessor() {
      public void process(CompileContext context, OutputFileObject out) {
        final Callbacks.Backend callback = DELTA_MAPPINGS_CALLBACK_KEY.get(context);
        if (callback != null) {
          final OutputFileObject.Content content = out.getContent();
          final File srcFile = out.getSourceFile();
          if (srcFile != null && content != null) {
            final String outputPath = FileUtil.toSystemIndependentName(out.getFile().getPath());
            final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
            final RootDescriptor moduleAndRoot = context.getModuleAndRoot(srcFile);
            final BuildDataManager dataManager = context.getDataManager();
            if (moduleAndRoot != null) {
              try {
                final String moduleName = moduleAndRoot.module.getName().toLowerCase(Locale.US);
                dataManager.getSourceToOutputMap(moduleName, context.isCompilingTests()).appendData(sourcePath, outputPath);
              }
              catch (Exception e) {
                context.processMessage(new CompilerMessage(BUILDER_NAME, e));
              }
            }
            final ClassReader reader = new ClassReader(content.getBuffer(), content.getOffset(), content.getLength());
            callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), reader);
          }
        }
      }
    });
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  public String getDescription() {
    return "Java Builder";
  }

  private static final Key<Set<File>> TEMPORARY_SOURCE_ROOTS_KEY = Key.create("_additional_source_roots_");

  public static void addTempSourcePathRoot(CompileContext context, File root) {
    Set<File> roots = TEMPORARY_SOURCE_ROOTS_KEY.get(context);
    if (roots == null) {
      roots = new HashSet<File>();
      TEMPORARY_SOURCE_ROOTS_KEY.set(context, roots);
    }
    roots.add(root);
  }

  public ExitCode build(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    try {
      final Set<File> filesToCompile = new HashSet<File>();
      final Set<File> formsToCompile = new HashSet<File>();

      context.processFilesToRecompile(chunk, new FileProcessor() {
        public boolean apply(Module module, File file, String sourceRoot) throws IOException {
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
      final CompilerExcludes excludes = context.getProject().getCompilerConfiguration().getExcludes();
      if (!context.isProjectRebuild()) {
        for (Iterator<File> formsIterator = formsToCompile.iterator(); formsIterator.hasNext(); ) {
          final File form = formsIterator.next();
          final RootDescriptor descriptor = context.getModuleAndRoot(form);
          if (descriptor == null) {
            continue;
          }
          for (RootDescriptor rd : context.getModuleRoots(descriptor.module)) {
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
        final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();
        for (File srcFile : filesToCompile) {
          final String srcPath = srcFile.getPath();
          final String formPath = sourceToFormMap.getState(srcPath);
          if (formPath == null) {
            continue;
          }
          final File formFile = new File(formPath);
          if (!excludes.isExcluded(formFile)) {
            if (formFile.exists()) {
              context.markDirty(formFile);
              formsToCompile.add(formFile);
            }
            sourceToFormMap.remove(srcPath);
          }
        }
      }

      final JavaBuilderLogger logger = context.getLoggingManager().getJavaBuilderLogger();
      if (logger.isEnabled()) {
        if (filesToCompile.size() > 0 && context.isMake()) {
          logger.log("Compiling files:");
          final String[] buffer = new String[filesToCompile.size()];
          int i = 0;
          for (final File f : filesToCompile) {
            buffer[i++] = FileUtil.toSystemIndependentName(f.getCanonicalPath());
          }
          Arrays.sort(buffer);
          for (final String s : buffer) {
            logger.log(s);
          }
          logger.log("End of files");
        }
      }

      return compile(context, chunk, filesToCompile, formsToCompile);
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

  private ExitCode compile(final CompileContext context, ModuleChunk chunk, Collection<File> files, Collection<File> forms)
    throws Exception {
    ExitCode exitCode = ExitCode.OK;

    final boolean hasSourcesToCompile = !files.isEmpty() || !forms.isEmpty();

    if (!hasSourcesToCompile && !context.hasRemovedSources()) {
      return exitCode;
    }

    final ProjectPaths paths = context.getProjectPaths();

    final boolean addNotNullAssertions = context.getProject().getCompilerConfiguration().isAddNotNullAssertions();

    final Collection<File> classpath =
      paths.getCompilationClasspath(chunk, context.isCompilingTests(), false/*context.isProjectRebuild()*/);
    final Collection<File> platformCp =
      paths.getPlatformCompilationClasspath(chunk, context.isCompilingTests(), false/*context.isProjectRebuild()*/);
    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);

    // begin compilation round
    final DiagnosticSink diagnosticSink = new DiagnosticSink(context);
    final OutputFilesSink outputSink = new OutputFilesSink(context);
    final Mappings delta = context.createDelta();
    DELTA_MAPPINGS_CALLBACK_KEY.set(context, delta.getCallback());
    try {
      if (hasSourcesToCompile) {
        final Set<File> sourcePath = TEMPORARY_SOURCE_ROOTS_KEY.get(context, Collections.<File>emptySet());

        final String chunkName = getChunkPresentableName(chunk);
        context.processMessage(new ProgressMessage("Compiling java [" + chunkName + "]"));

        final boolean compiledOk =
          files.isEmpty() || compileJava(chunk, files, classpath, platformCp, sourcePath, outs, context, diagnosticSink, outputSink);

        context.checkCanceled();

        if (!forms.isEmpty() || addNotNullAssertions) {
          final Map<File, String> chunkSourcePath = ProjectPaths.getSourceRootsWithDependents(chunk, context.isCompilingTests());
          final InstrumentationClassFinder finder = createInstrumentationClassFinder(platformCp, classpath, chunkSourcePath, outputSink);

          try {
            if (!forms.isEmpty()) {
              try {
                context.processMessage(new ProgressMessage("Instrumenting forms [" + chunkName + "]"));
                instrumentForms(context, chunk, chunkSourcePath, finder, forms, outputSink);
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
        if (diagnosticSink.getErrorCount() > 0) {
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

      if (updateMappings(context, delta, chunk, files, successfullyCompiled)) {
        exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
    }

    if (exitCode != ExitCode.ADDITIONAL_PASS_REQUIRED) {
      final Set<File> tempRoots = TEMPORARY_SOURCE_ROOTS_KEY.get(context);
      TEMPORARY_SOURCE_ROOTS_KEY.set(context, null);
      if (tempRoots != null && tempRoots.size() > 0) {
        FileUtil.asyncDelete(tempRoots);
      }
    }
    return exitCode;
  }

  private static String getChunkPresentableName(ModuleChunk chunk) {
    final Set<Module> modules = chunk.getModules();
    if (modules.isEmpty()) {
      return "<empty>";
    }
    if (modules.size() == 1) {
      return modules.iterator().next().getName();
    }
    final StringBuilder buf = new StringBuilder();
    for (Module module : modules) {
      if (buf.length() > 0) {
        buf.append(",");
      }
      buf.append(module.getName());
    }
    return buf.toString();
  }

  private boolean compileJava(ModuleChunk chunk, Collection<File> files,
                              Collection<File> classpath,
                              Collection<File> platformCp,
                              Collection<File> sourcePath,
                              Map<File, Set<File>> outs,
                              CompileContext context,
                              DiagnosticOutputConsumer diagnosticSink,
                              final OutputFileConsumer outputSink) throws Exception {
    final List<String> options = getCompilationOptions(context, chunk);
    final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
    try {
      final boolean rc;
      if (USE_EMBEDDED_JAVAC) {
        rc = JavacMain.compile(
          options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, classesConsumer, context.getCancelStatus()
        );
      }
      else {
        final JavacServerClient client = ensureJavacServerLaunched(context);
        final RequestFuture<JavacServerResponseHandler> future = client.sendCompileRequest(
          options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, classesConsumer
        );
        while (!future.waitFor(100L, TimeUnit.MILLISECONDS)) {
          if (context.isCanceled()) {
            future.cancel(false);
          }
        }
        rc = future.getResponseHandler().isTerminatedSuccessfully();
      }
      return rc;
    }
    finally {
      ensurePendingTasksCompleted();
    }
  }

  private void ensurePendingTasksCompleted() {
    synchronized (myCounterLock) {
      while (myTasksInProgress > 0) {
        try {
          myCounterLock.wait();
        }
        catch (InterruptedException ignored) {
        }
      }
    }
  }

  private void submitAsyncTask(final Runnable taskRunnable) {
    synchronized (myCounterLock) {
      myTasksInProgress++;
    }
    myTaskRunner.submit(new Runnable() {
      public void run() {
        try {
          taskRunnable.run();
        }
        finally {
          synchronized (myCounterLock) {
            myTasksInProgress = Math.max(0, myTasksInProgress - 1);
            if (myTasksInProgress == 0) {
              myCounterLock.notifyAll();
            }
          }
        }
      }
    });
  }

  private static JavacServerClient ensureJavacServerLaunched(CompileContext context) throws Exception {
    final ExternalJavacDescriptor descriptor = ExternalJavacDescriptor.KEY.get(context);
    if (descriptor != null) {
      return descriptor.client;
    }
    // start server here
    final String hostString = System.getProperty(GlobalOptions.HOSTNAME_OPTION, "localhost");
    final int port = findFreePort();
    final int heapSize = getJavacServerHeapSize(context);

    // defaulting to the same jdk that used to run the server
    String javaHome = SystemProperties.getJavaHome();
    int javaVersion = convertToNumber(SystemProperties.getJavaVersion());

    for (JavaSdk sdk : context.getProjectDescriptor().getProjectJavaSdks()) {
      final String version = sdk.getVersion();
      final int ver = convertToNumber(version);
      if (ver > javaVersion) {
        javaVersion = ver;
        javaHome = sdk.getHomePath();
      }
    }

    final BaseOSProcessHandler processHandler = JavacServerBootstrap.launchJavacServer(
      javaHome, heapSize, port, Paths.getSystemRoot(), getCompilationVMOptions(context)
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

  private static int convertToNumber(final String ver) {
    final String prefix = "1.";
    if (ver.startsWith(prefix)) {
      final String versionNumberString;
      final int dotIndex = ver.indexOf(".", prefix.length());
      if (dotIndex > 0) {
        versionNumberString = ver.substring(prefix.length(), dotIndex);
      }
      else {
        versionNumberString = ver.substring(prefix.length());
      }
      try {
        return Integer.parseInt(versionNumberString);
      }
      catch (NumberFormatException ignored) {
      }
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
    int heapSize = 512;
    final Project project = context.getProject();
    final Map<String, String> javacOpts = project.getCompilerConfiguration().getJavacOptions();
    final String hSize = javacOpts.get("MAXIMUM_HEAP_SIZE");
    if (hSize != null) {
      try {
        heapSize = Integer.parseInt(hSize);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return heapSize;
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
      loadJavacOptions(context);
      cached = JAVAC_VM_OPTIONS.get(context);
    }
    return cached;
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk) {
    List<String> cached = JAVAC_OPTIONS.get(context);
    if (cached == null) {
      loadJavacOptions(context);
      cached = JAVAC_OPTIONS.get(context);
    }
    final List<String> options = new ArrayList<String>(cached);
    final Module module = chunk.getModules().iterator().next();
    final String langlevel = module.getLanguageLevel();
    if (!StringUtil.isEmpty(langlevel)) {
      options.add("-source");
      options.add(langlevel);
    }
    return options;
  }

  private static void loadJavacOptions(CompileContext context) {
    final List<String> options = new ArrayList<String>();
    final List<String> vmOptions = new ArrayList<String>();

    //options.add("-verbose");
    final Project project = context.getProject();
    final Map<String, String> javacOpts = project.getCompilerConfiguration().getJavacOptions();
    final boolean debugInfo = !"false".equals(javacOpts.get("DEBUGGING_INFO"));
    final boolean nowarn = "true".equals(javacOpts.get("GENERATE_NO_WARNINGS"));
    final boolean deprecation = !"false".equals(javacOpts.get("DEPRECATION"));
    if (debugInfo) {
      options.add("-g");
    }
    if (deprecation) {
      options.add("-deprecation");
    }
    if (nowarn) {
      options.add("-nowarn");
    }

    final String customArgs = javacOpts.get("ADDITIONAL_OPTIONS_STRING");
    boolean isEncodingSet = false;
    if (customArgs != null) {
      final StringTokenizer tokenizer = new StringTokenizer(customArgs, " \t\r\n");
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        if ("-g".equals(token) || "-deprecation".equals(token) || "-nowarn".equals(token) || "-verbose".equals(token)) {
          continue;
        }
        if (token.startsWith("-J-")) {
          vmOptions.add(token.substring("-J".length()));
        }
        else {
          options.add(token);
        }
        if ("-encoding".equals(token)) {
          isEncodingSet = true;
        }
      }
    }

    if (!isEncodingSet && !StringUtil.isEmpty(project.getProjectCharset())) {
      options.add("-encoding");
      options.add(project.getProjectCharset());
    }

    JAVAC_OPTIONS.set(context, options);
    JAVAC_VM_OPTIONS.set(context, vmOptions);
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new HashMap<File, Set<File>>();
    final boolean compilingTests = context.isCompilingTests();
    for (Module module : chunk.getModules()) {
      final String outputPath;
      final Collection<String> srcPaths;
      if (compilingTests) {
        outputPath = module.getTestOutputPath();
        srcPaths = module.getTestRoots();
      }
      else {
        outputPath = module.getOutputPath();
        srcPaths = module.getSourceRoots();
      }
      final Set<File> roots = new HashSet<File>();
      for (String path : srcPaths) {
        roots.add(new File(path));
      }
      map.put(new File(outputPath), roots);
    }
    return map;
  }

  // todo: probably instrument other NotNull-like annotations defined in project settings?
  private static void instrumentNotNull(CompileContext context, OutputFilesSink sink, final InstrumentationClassFinder finder) {
    for (final OutputFileObject fileObject : sink.getFileObjects()) {
      final OutputFileObject.Content originalContent = fileObject.getContent();
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
    final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();

    final Map<String, OutputFileObject> compiledClassNames = new HashMap<String, OutputFileObject>();
    for (OutputFileObject fileObject : outputSink.getFileObjects()) {
      compiledClassNames.put(fileObject.getClassName(), fileObject);
    }

    final MyNestedFormLoader nestedFormsLoader =
      new MyNestedFormLoader(chunkSourcePath, ProjectPaths.getOutputPathsWithDependents(chunk, context.isCompilingTests()));

    for (File formFile : formsToInstrument) {
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(formFile.toURI().toURL(), new CompiledClassPropertiesProvider(finder.getLoader()));
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
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
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Class to bind does not exist: " + classToBind,
                                                   formFile.getAbsolutePath()));
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
            new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.WARNING, warning.getErrorMessage(), formFile.getAbsolutePath()));
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
            sourceToFormMap.update(sourceFile.getPath(), formFile.getPath());
          }
        }
      }
      catch (Exception e) {
        success = false;
        context.processMessage(new CompilerMessage(FORMS_BUILDER_NAME, BuildMessage.Kind.ERROR, "Forms instrumentation failed" + e.getMessage(),
                                                   formFile.getAbsolutePath()));
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
    reader.accept(new EmptyVisitor() {
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
      submitAsyncTask(new Runnable() {
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
        //if (line.startsWith("[") && line.endsWith("]")) {
        //  final String message = line.substring(1, line.length() - 1);
        //  if (message.startsWith("parsing")) {
        //    myContext.processMessage(new ProgressMessage("Parsing sources..."));
        //  }
        //  else {
        //    if (!message.startsWith("total ") && !message.startsWith("loading ") && !message.startsWith("wrote ")) {
        //      myContext.processMessage(new ProgressMessage(FileUtil.toSystemDependentName(message)));
        //    }
        //  }
        //}
        //else
        if (line.contains("java.lang.OutOfMemoryError")) {
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "OutOfMemoryError: insufficient memory"));
        }
        else {
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, line));
        }
      }
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
      final File sourceFile = source != null ? Paths.convertToFile(source.toUri()) : null;
      final String srcPath = sourceFile != null ? FileUtil.toSystemIndependentName(sourceFile.getPath()) : null;
      myContext.processMessage(
        new CompilerMessage(BUILDER_NAME, kind, diagnostic.getMessage(Locale.US), srcPath, diagnostic.getStartPosition(),
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
      final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
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
    private final CompileContext myCompileContext;
    private final OutputFileConsumer myDelegateOutputFileSink;

    public ClassProcessingConsumer(CompileContext compileContext, OutputFileConsumer sink) {
      myCompileContext = compileContext;
      myDelegateOutputFileSink = sink != null ? sink : new OutputFileConsumer() {
        public void save(@NotNull OutputFileObject fileObject) {
          throw new RuntimeException("Output sink for compiler was not specified");
        }
      };
    }

    public void save(@NotNull final OutputFileObject fileObject) {
      submitAsyncTask(new Runnable() {
        public void run() {
          try {
            for (ClassPostProcessor processor : myClassProcessors) {
              processor.process(myCompileContext, fileObject);
            }
          }
          finally {
            myDelegateOutputFileSink.save(fileObject);
          }
        }
      });
    }
  }
}
