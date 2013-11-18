/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.java;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderExtension;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.*;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.jetbrains.jps.model.java.compiler.*;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.service.JpsServiceManager;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.JavaBuilder");
  public static final String BUILDER_NAME = "java";
  private static final String JAVA_EXTENSION = "java";
  private static final String DOT_JAVA_EXTENSION = "." + JAVA_EXTENSION;
  public static final boolean USE_EMBEDDED_JAVAC = System.getProperty(GlobalOptions.USE_EXTERNAL_JAVAC_OPTION) == null;
  private static final Key<Integer> JAVA_COMPILER_VERSION_KEY = Key.create("_java_compiler_version_");
  public static final Key<Boolean> IS_ENABLED = Key.create("_java_compiler_enabled_");
  private static final Key<AtomicReference<String>> COMPILER_VERSION_INFO = Key.create("_java_compiler_version_info_");

  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-target"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-g", "-deprecation", "-nowarn", "-verbose", "-proc:none", "-proc:only", "-proceedOnError"
  ));

  public static final FileFilter JAVA_SOURCES_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new FileFilter() {
      public boolean accept(File file) {
        return file.getPath().endsWith(DOT_JAVA_EXTENSION);
      }
    } :
    new FileFilter() {
      public boolean accept(File file) {
        return StringUtil.endsWithIgnoreCase(file.getPath(), DOT_JAVA_EXTENSION);
      }
    };

  private final Executor myTaskRunner;
  private static final List<ClassPostProcessor> ourClassProcessors = new ArrayList<ClassPostProcessor>();
  private static final Set<JpsModuleType<?>> ourCompilableModuleTypes;
  static {
    ourCompilableModuleTypes = new HashSet<JpsModuleType<?>>();
    for (JavaBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(JavaBuilderExtension.class)) {
      ourCompilableModuleTypes.addAll(extension.getCompilableModuleTypes());
    }
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

  @Override
  public void buildStarted(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    final String compilerId = config == null? JavaCompilers.JAVAC_ID : config.getJavaCompilerId();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Java compiler ID: " + compilerId);
    }
    final boolean isJavac = JavaCompilers.JAVAC_ID.equalsIgnoreCase(compilerId) || JavaCompilers.JAVAC_API_ID.equalsIgnoreCase(compilerId);
    final boolean isEclipse = JavaCompilers.ECLIPSE_ID.equalsIgnoreCase(compilerId) || JavaCompilers.ECLIPSE_EMBEDDED_ID.equalsIgnoreCase(compilerId);
    IS_ENABLED.set(context, isJavac || isEclipse);
    String messageText = null;
    if (isJavac) {
      messageText = "Using javac " + System.getProperty("java.version") + " to compile java sources";
    }
    else if (isEclipse) {
      messageText = "Using eclipse compiler to compile java sources";
    }
    COMPILER_VERSION_INFO.set(context, new AtomicReference<String>(messageText));
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.singletonList(JAVA_EXTENSION);
  }

  public ExitCode build(@NotNull CompileContext context,
                        @NotNull ModuleChunk chunk,
                        @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        @NotNull OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    if (!IS_ENABLED.get(context, Boolean.TRUE)) {
      return ExitCode.NOTHING_DONE;
    }
    return doBuild(context, chunk, dirtyFilesHolder, outputConsumer);
  }

  public ExitCode doBuild(@NotNull CompileContext context,
                          @NotNull ModuleChunk chunk,
                          @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          @NotNull OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    try {
      final Set<File> filesToCompile = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

      dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
        public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
          if (JAVA_SOURCES_FILTER.accept(file) && ourCompilableModuleTypes.contains(target.getModule().getModuleType())) {
            filesToCompile.add(file);
          }
          return true;
        }
      });

      if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
        final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        if (logger.isEnabled()) {
          if (filesToCompile.size() > 0) {
            logger.logCompiledFiles(filesToCompile, BUILDER_NAME, "Compiling files:");
          }
        }
      }

      return compile(context, chunk, dirtyFilesHolder, filesToCompile, outputConsumer);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      String message = e.getMessage();
      if (message == null) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        try {
          e.printStackTrace(stream);
        }
        finally {
          stream.close();
        }
        message = "Internal error: \n" + out.toString();
      }
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      throw new StopBuildException();
    }
  }

  private ExitCode compile(final CompileContext context,
                           ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                           Collection<File> files,
                           OutputConsumer outputConsumer)
    throws Exception {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final boolean hasSourcesToCompile = !files.isEmpty();

    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return exitCode;
    }

    final ProjectDescriptor pd = context.getProjectDescriptor();

    JavaBuilderUtil.ensureModuleHasJdk(chunk.representativeTarget().getModule(), context, BUILDER_NAME);
    final Collection<File> classpath = ProjectPaths.getCompilationClasspath(chunk, false/*context.isProjectRebuild()*/);
    final Collection<File> platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false/*context.isProjectRebuild()*/);

    // begin compilation round
    final Mappings delta = pd.dataManager.getMappings().createDelta();
    final Callbacks.Backend mappingsCallback = delta.getCallback();
    final OutputFilesSink outputSink = new OutputFilesSink(context, outputConsumer, mappingsCallback, chunk.getName());
    try {
      if (hasSourcesToCompile) {
        final AtomicReference<String> ref = COMPILER_VERSION_INFO.get(context);
        final String versionInfo = ref.getAndSet(null); // display compiler version info only once per compile session
        if (versionInfo != null) {
          LOG.info(versionInfo);
          context.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, versionInfo));
        }
        exitCode = ExitCode.OK;

        final Set<File> srcPath = new HashSet<File>();
        final BuildRootIndex index = pd.getBuildRootIndex();
        for (ModuleBuildTarget target : chunk.getTargets()) {
          for (JavaSourceRootDescriptor rd : index.getTempTargetRoots(target, context)) {
            srcPath.add(rd.root);
          }
        }
        final DiagnosticSink diagnosticSink = new DiagnosticSink(context);
        
        final String chunkName = chunk.getName();
        context.processMessage(new ProgressMessage("Parsing java... [" + chunkName + "]"));

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
          try {
            compiledOk = compileJava(context, chunk, files, classpath, platformCp, srcPath, diagnosticSink, outputSink);
          }
          finally {
            // heuristic: incorrect paths data recovery, so that the next make should not contain non-existing sources in 'recompile' list
            for (File file : diagnosticSink.getFilesWithErrors()) {
              if (!file.exists()) {
                FSOperations.markDeleted(context, file);
              }
            }
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
          throw new StopBuildException(
            "Compilation failed: errors: " + diagnosticSink.getErrorCount() + "; warnings: " + diagnosticSink.getWarningCount()
          );
        }
      }
    }
    finally {
      if (JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, files, outputSink.getSuccessfullyCompiled())) {
        exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
    }

    return exitCode;
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

    final JpsJavaExtensionService javaExt = JpsJavaExtensionService.getInstance();
    final JpsJavaCompilerConfiguration compilerConfig = javaExt.getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert compilerConfig != null;

    final Set<JpsModule> modules = chunk.getModules();
    ProcessorConfigProfile profile = null;
    if (modules.size() == 1) {
      final JpsModule module = modules.iterator().next();
      profile = compilerConfig.getAnnotationProcessingProfile(module);
    }
    else {
      // perform cycle-related validations
      Pair<String, LanguageLevel> pair = null;
      for (JpsModule module : modules) {
        final LanguageLevel moduleLevel = javaExt.getLanguageLevel(module);
        if (pair == null) {
          pair = Pair.create(module.getName(), moduleLevel); // first value
        }
        else {
          if (!Comparing.equal(pair.getSecond(), moduleLevel)) {
            final String message = "Modules " + pair.getFirst()+ " and " +module.getName() + " must have the same language level because of cyclic dependencies between them";
            diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
            return true;
          }
        }
      }

      // check that all chunk modules are excluded from annotation processing
      for (JpsModule module : modules) {
        final ProcessorConfigProfile prof = compilerConfig.getAnnotationProcessingProfile(module);
        if (prof.isEnabled()) {
          final String message = "Annotation processing is not supported for module cycles. Please ensure that all modules from cycle [" + chunk.getName() + "] are excluded from annotation processing";
          diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
          return true;
        }
      }
    }

    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk, profile);
    final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Compiling chunk [" + chunk.getName() + "] with options: \"" + StringUtil.join(options, " ") + "\"");
    }
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
    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    final String compilerId = configuration != null? configuration.getJavaCompilerId() : null;
    return JavaCompilers.ECLIPSE_ID.equalsIgnoreCase(compilerId) || JavaCompilers.ECLIPSE_EMBEDDED_ID.equalsIgnoreCase(compilerId);
  }

  private void submitAsyncTask(final CompileContext context, final Runnable taskRunnable) {
    final TasksCounter counter = COUNTER_KEY.get(context);

    assert counter != null;

    counter.incTaskCount();
    myTaskRunner.execute(new Runnable() {
      public void run() {
        try {
          taskRunnable.run();
        }
        catch (Throwable e) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, e));
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
    final int port = findFreePort();
    final int heapSize = getJavacServerHeapSize(context);

    // use the same jdk that used to run the build process
    final String javaHome = SystemProperties.getJavaHome();

    final BaseOSProcessHandler processHandler = JavacServerBootstrap.launchJavacServer(
      javaHome, heapSize, port, Utils.getSystemRoot(), getCompilationVMOptions(context), useEclipseCompiler(context)
    );
    final JavacServerClient client = new JavacServerClient();
    try {
      client.connect("127.0.0.1", port);
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

  private static final Key<List<String>> JAVAC_OPTIONS = Key.create("_javac_options_");
  private static final Key<List<String>> JAVAC_VM_OPTIONS = Key.create("_javac_vm_options_");
  private static final Key<String> USER_DEFINED_BYTECODE_TARGET = Key.create("_user_defined_bytecode_target_");

  private static List<String> getCompilationVMOptions(CompileContext context) {
    List<String> cached = JAVAC_VM_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context);
      cached = JAVAC_VM_OPTIONS.get(context);
    }
    return cached;
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk, @Nullable ProcessorConfigProfile profile) {
    List<String> cached = JAVAC_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context);
      cached = JAVAC_OPTIONS.get(context);
      assert cached != null : context;
    }

    List<String> options = new ArrayList<String>(cached);
    addCompilationOptions(options, context, chunk, profile);
    return options;
  }

  public static void addCompilationOptions(List<String> options, CompileContext context, ModuleChunk chunk, @Nullable ProcessorConfigProfile profile) {
    if (!isEncodingSet(options)) {
      final CompilerEncodingConfiguration config = context.getProjectDescriptor().getEncodingConfiguration();
      final String encoding = config.getPreferredModuleChunkEncoding(chunk);
      if (config.getAllModuleChunkEncodings(chunk).size() > 1) {
        final StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("Multiple encodings set for module chunk ").append(chunk.getName());
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
    
    if (bytecodeTarget == null) {
      // last resort and backward compatibility: 
      // check if user explicitly defined bytecode target in additional compiler options
      bytecodeTarget = USER_DEFINED_BYTECODE_TARGET.get(context);
    }

    final int compilerSdkVersion = getCompilerSdkVersion(context);
    
    if (bytecodeTarget != null) {
      options.add("-target");
      if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) { 
        // if compiler is newer than module JDK
        final int userSpecifiedTargetVersion = convertToNumber(bytecodeTarget);
        if (userSpecifiedTargetVersion > 0 && userSpecifiedTargetVersion <= compilerSdkVersion) {
          // if user-specified bytecode version can be determined and is supported by compiler
          if (userSpecifiedTargetVersion > chunkSdkVersion) {
            // and user-specified bytecode target level is higher than the highest one supported by the target JDK,
            // force compiler to use highest-available bytecode target version that is supported by the chunk JDK.
            bytecodeTarget = "1." + chunkSdkVersion;
          }
        }
        // otherwise let compiler display compilation error about incorrectly set bytecode target version
      }
      options.add(bytecodeTarget);
    }
    else {
      if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
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

      final Set<String> processors = profile.getProcessors();
      if (!processors.isEmpty()) {
        options.add("-processor");
        options.add(StringUtil.join(processors, ","));
      }

      for (Map.Entry<String, String> optionEntry : profile.getProcessorOptions().entrySet()) {
        options.add("-A" + optionEntry.getKey() + "=" + optionEntry.getValue());
      }

      final File srcOutput = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(
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
    JAVA_COMPILER_VERSION_KEY.set(context, javaVersion);
    return javaVersion;
  }

  private static void loadCommonJavacOptions(CompileContext context) {
    final List<String> options = new ArrayList<String>();
    final List<String> vmOptions = new ArrayList<String>();

    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
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
      boolean targetOptionFound = false;
      while (customOptsTokenizer.hasMoreTokens()) {
        final String userOption = customOptsTokenizer.nextToken();
        if (FILTERED_OPTIONS.contains(userOption)) {
          skip = true;
          targetOptionFound = "-target".equals(userOption);
          continue;
        }
        if (skip) {
          skip = false;
          if (targetOptionFound) {
            targetOptionFound = false;
            USER_DEFINED_BYTECODE_TARGET.set(context, userOption);
          }
        }
        else {
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

    if (useEclipseCompiler(context)) {
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
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new THashMap<File, Set<File>>(FileUtil.FILE_HASHING_STRATEGY);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      final File outputDir = target.getOutputDir();
      if (outputDir == null) {
        continue;
      }
      final Set<File> roots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      for (JavaSourceRootDescriptor descriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
        roots.add(descriptor.root);
      }
      map.put(outputDir, roots);
    }
    return map;
  }

  private static class DiagnosticSink implements DiagnosticOutputConsumer {
    private final CompileContext myContext;
    private volatile int myErrorCount = 0;
    private volatile int myWarningCount = 0;
    private final Set<File> myFilesWithErrors = new HashSet<File>();

    public DiagnosticSink(CompileContext context) {
      myContext = context;
    }

    @Override
    public void javaFileLoaded(File file) {
    }

    public void registerImports(final String className, final Collection<String> imports, final Collection<String> staticImports) {
      //submitAsyncTask(myContext, new Runnable() {
      //  public void run() {
      //    final Callbacks.Backend callback = DELTA_MAPPINGS_CALLBACK_KEY.get(myContext);
      //    if (callback != null) {
      //      callback.registerImports(className, imports, staticImports);
      //    }
      //  }
      //});
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

    private static BuildMessage.Kind getKindByMessageText(String line) {
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
      File sourceFile = null;
      try {
        // for eclipse compiler just an attempt to call getSource() may lead to an NPE,
        // so calling this method under try/catch to avoid induced compiler errors
        final JavaFileObject source = diagnostic.getSource();
        sourceFile = source != null ? Utils.convertToFile(source.toUri()) : null;
      }
      catch (Exception e) {
        LOG.info(e);
      }
      final String srcPath;
      if (sourceFile != null) {
        myFilesWithErrors.add(sourceFile);
        srcPath = FileUtil.toSystemIndependentName(sourceFile.getPath());
      }
      else {
        srcPath = null;
      }
      String message = diagnostic.getMessage(Locale.US);
      if (Utils.IS_TEST_MODE) {
        LOG.info(message);
      }
      myContext.processMessage(new CompilerMessage(
        BUILDER_NAME, kind, message, srcPath, diagnostic.getStartPosition(),
        diagnostic.getEndPosition(), diagnostic.getPosition(), diagnostic.getLineNumber(),
        diagnostic.getColumnNumber()
      ));
    }

    public int getErrorCount() {
      return myErrorCount;
    }

    public int getWarningCount() {
      return myWarningCount;
    }

    public Collection<File> getFilesWithErrors() {
      return myFilesWithErrors;
    }
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
      // generated files must be saved synchronously, because some compilers (e.g. eclipse)
      // may want to read them for further compilation
      try {
        final BinaryContent content = fileObject.getContent();
        final File file = fileObject.getFile();
        if (content != null) {
          content.saveToFile(file);
        }
        else {
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Missing content for file " + file.getPath()));
        }
      }
      catch (IOException e) {
        myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
      }

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
