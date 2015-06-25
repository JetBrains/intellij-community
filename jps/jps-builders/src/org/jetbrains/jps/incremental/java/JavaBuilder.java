/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.PersistentEnumeratorBase;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.JavaBuilderExtension;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
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
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.PathMacroUtil;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.*;
import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.JavaBuilder");
  public static final String BUILDER_NAME = "java";
  private static final String JAVA_EXTENSION = "java";
  private static final Key<Integer> JAVA_COMPILER_VERSION_KEY = Key.create("_java_compiler_version_");
  public static final Key<Boolean> IS_ENABLED = Key.create("_java_compiler_enabled_");
  private static final Key<JavaCompilingTool> COMPILING_TOOL = Key.create("_java_compiling_tool_");
  private static final Key<AtomicReference<String>> COMPILER_VERSION_INFO = Key.create("_java_compiler_version_info_");

  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-target"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-g", "-deprecation", "-nowarn", "-verbose", "-proc:none", "-proc:only", "-proceedOnError"
  ));

  public static final FileFilter JAVA_SOURCES_FILTER = FileFilters.withExtension(JAVA_EXTENSION);
  private static final String RT_JAR_PATH_SUFFIX = File.separator + "rt.jar";

  private final Executor myTaskRunner;
  private static final List<ClassPostProcessor> ourClassProcessors = new ArrayList<ClassPostProcessor>();
  private static final Set<JpsModuleType<?>> ourCompilableModuleTypes;
  @Nullable
  private static final File ourDefaultRtJar;
  static {
    ourCompilableModuleTypes = new HashSet<JpsModuleType<?>>();
    for (JavaBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(JavaBuilderExtension.class)) {
      ourCompilableModuleTypes.addAll(extension.getCompilableModuleTypes());
    }
    File rtJar = null;
    StringTokenizer tokenizer = new StringTokenizer(System.getProperty("sun.boot.class.path", ""), File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      final String path = tokenizer.nextToken();
      if (isRtJarPath(path)) {
        rtJar = new File(path);
        break;
      }
    }
    ourDefaultRtJar = rtJar;
  }
  
  private static boolean isRtJarPath(String path) {
    if (StringUtil.endsWithIgnoreCase(path, RT_JAR_PATH_SUFFIX)) {
      return true;
    }
    return RT_JAR_PATH_SUFFIX.charAt(0) != '/' && StringUtil.endsWithIgnoreCase(path, "/rt.jar");
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
    JavaCompilingTool compilingTool = JavaBuilderUtil.findCompilingTool(compilerId);
    COMPILING_TOOL.set(context, compilingTool);
    String messageText = compilingTool != null ? "Using " + compilingTool.getDescription() + " to compile java sources" : null;
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
    JavaCompilingTool compilingTool = COMPILING_TOOL.get(context);
    if (!IS_ENABLED.get(context, Boolean.TRUE) || compilingTool == null) {
      return ExitCode.NOTHING_DONE;
    }
    return doBuild(context, chunk, dirtyFilesHolder, outputConsumer, compilingTool);
  }

  public ExitCode doBuild(@NotNull CompileContext context,
                          @NotNull ModuleChunk chunk,
                          @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          @NotNull OutputConsumer outputConsumer, JavaCompilingTool compilingTool) throws ProjectBuildException, IOException {
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

      return compile(context, chunk, dirtyFilesHolder, filesToCompile, outputConsumer, compilingTool);
    }
    catch (BuildDataCorruptedException e) {
      throw e;
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (PersistentEnumeratorBase.CorruptedException e) {
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
                           OutputConsumer outputConsumer, @NotNull JavaCompilingTool compilingTool)
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
    final OutputFilesSink outputSink = new OutputFilesSink(context, outputConsumer, JavaBuilderUtil.getDependenciesRegistrar(context), chunk.getPresentableShortName());
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
        context.processMessage(new ProgressMessage("Parsing java... [" + chunk.getPresentableShortName() + "]"));

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
            compiledOk = compileJava(context, chunk, files, classpath, platformCp, srcPath, diagnosticSink, outputSink, compilingTool);
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
      JavaBuilderUtil.registerFilesToCompile(context, files);
      JavaBuilderUtil.registerSuccessfullyCompiled(context, outputSink.getSuccessfullyCompiled());
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
    final OutputFileConsumer outputSink, JavaCompilingTool compilingTool) throws Exception {

    final TasksCounter counter = new TasksCounter();
    COUNTER_KEY.set(context, counter);

    final JpsJavaExtensionService javaExt = JpsJavaExtensionService.getInstance();
    final JpsJavaCompilerConfiguration compilerConfig = javaExt.getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert compilerConfig != null;

    final Set<JpsModule> modules = chunk.getModules();
    ProcessorConfigProfile profile = null;
    if (modules.size() == 1) {
      profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next());
    }
    else {
      String message = validateCycle(chunk, javaExt, compilerConfig, modules);
      if (message != null) {
        diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
        return true;
      }
    }

    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk, profile, compilingTool);
    final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Compiling chunk [" + chunk.getName() + "] with options: \"" + StringUtil.join(options, " ") + "\"");
    }
    try {
      final int chunkSdkVersion = getChunkSdkVersion(chunk);
      
      Collection<File> _platformCp = calcEffectivePlatformCp(platformCp, options, compilingTool);
      if (_platformCp == null) {
        context.processMessage(
          new CompilerMessage(
            BUILDER_NAME, BuildMessage.Kind.ERROR,
            "Compact compilation profile was requested, but target platform for module \"" + chunk.getName() + "\" differs from javac's platform (" + System.getProperty("java.version") + ")\nCompilation profiles are not supported for such configuration"
          )
        );
        return true;
      }

      if (chunkSdkVersion >= 9 && !_platformCp.isEmpty()) {
        // if chunk's SDK is 9 or higher, there is no way to specify full platform classpath
        // because platform classes are stored in jimage binary files with unknown format.
        // Because of this we are clearing platform classpath so that javac will resolve against its own bootclasspath
        // and prepending additional jars from the JDK configuration to compilation classpath
        final Collection<File> joined = new ArrayList<File>(_platformCp.size() + classpath.size());
        joined.addAll(_platformCp);
        joined.addAll(classpath);
        classpath = joined;
        _platformCp = Collections.emptyList();
      }

      final boolean rc;
      if (!shouldForkCompilerProcess(context, chunkSdkVersion)) {
        rc = JavacMain.compile(
          options, files, classpath, _platformCp, sourcePath, outs, diagnosticSink, classesConsumer, context.getCancelStatus(), compilingTool
        );
      }
      else {
        // fork external javac
        final String sdkHome = getChunkSdkHome(chunk);
        if (sdkHome == null) {
          diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Cannot start javac process for " + chunk.getName() + ": unknown JDK home path.\nPlease check project configuration."));
          return true;
        }

        final List<String> vmOptions = getCompilationVMOptions(context, compilingTool);
        final ExternalJavacManager server = ensureJavacServerStarted(context);
        rc = server.forkJavac(
          sdkHome, getExternalJavacHeapSize(context), vmOptions, options, _platformCp, classpath, sourcePath, files, outs, diagnosticSink, classesConsumer, compilingTool, context.getCancelStatus()
        );
      }
      return rc;
    }
    finally {
      counter.await();
    }
  }

  private static int getExternalJavacHeapSize(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final JpsJavaCompilerOptions options = config.getCurrentCompilerOptions();
    return options.MAXIMUM_HEAP_SIZE;
  }
  @Nullable
  public static String validateCycle(ModuleChunk chunk,
                                     JpsJavaExtensionService javaExt,
                                     JpsJavaCompilerConfiguration compilerConfig, Set<JpsModule> modules) {
    Pair<String, LanguageLevel> pair = null;
    for (JpsModule module : modules) {
      final LanguageLevel moduleLevel = javaExt.getLanguageLevel(module);
      if (pair == null) {
        pair = Pair.create(module.getName(), moduleLevel); // first value
      }
      else {
        if (!Comparing.equal(pair.getSecond(), moduleLevel)) {
          return "Modules " +
                 pair.getFirst() +
                 " and " +
                 module.getName() +
                 " must have the same language level because of cyclic dependencies between them";
        }
      }
    }

    // check that all chunk modules are excluded from annotation processing
    for (JpsModule module : modules) {
      final ProcessorConfigProfile prof = compilerConfig.getAnnotationProcessingProfile(module);
      if (prof.isEnabled()) {
        return "Annotation processing is not supported for module cycles. Please ensure that all modules from cycle [" +
               chunk.getName() +
               "] are excluded from annotation processing";
      }
    }
    return null;
  }

  private static boolean shouldForkCompilerProcess(CompileContext context, final int chunkSdkVersion) {
    final int compilerSdkVersion = getCompilerSdkVersion(context);
    if (compilerSdkVersion < 9 || chunkSdkVersion < 0) {
      // javac up to version 9 supports all previous releases
      // or: was not able to determine jdk version, so assuming in-process compiler
      return false;
    }
    if (chunkSdkVersion >= 9 && compilerSdkVersion != chunkSdkVersion) {
      // For these SDK versions libraries are stored in jimage files with unknown format and currently
      // there is no way to specify full platform classpath for cross-compilation purposes.
      // We have for fork compiler process with corresponding SDK runtime to ensure that compiler resolves platform classes 
      // against the SDK version specified for the given chunk.
      return true;
    }
    // according to JEP 182: Retiring javac "one plus three back" policy
    return Math.abs(compilerSdkVersion - chunkSdkVersion) > 3; 
  }

  // If platformCp of the build process is the same as the target plafform, do not specify platformCp explicitly
  // this will allow javac to resolve against ct.sym file, which is required for the "compilation profiles" feature
  @Nullable
  private static Collection<File> calcEffectivePlatformCp(Collection<File> platformCp, List<String> options, JavaCompilingTool compilingTool) {
    if (ourDefaultRtJar == null || !(compilingTool instanceof JavacCompilerTool)) {
      return platformCp;
    }
    boolean profileFeatureRequested = false;
    for (String option : options) {
      if ("-profile".equalsIgnoreCase(option)) {
        profileFeatureRequested = true;
        break;
      }
    }
    if (!profileFeatureRequested) {
      return platformCp;
    }
    boolean isTargetPlatformSameAsBuildRuntime = false;
    for (File file : platformCp) {
      if (FileUtil.filesEqual(file, ourDefaultRtJar)) {
        isTargetPlatformSameAsBuildRuntime = true;
        break;
      }
    }
    if (!isTargetPlatformSameAsBuildRuntime) {
      // compact profile was requested, but we have to use alternative platform classpath to meet project settings
      // consider this a compile error and let user re-configure the project 
      return null;
    }
    // returning empty list will force default behaviour for platform classpath calculation 
    // javac will resolve against its own bootclasspath and use ct.sym file when available 
    return Collections.emptyList();
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

  private static synchronized ExternalJavacManager ensureJavacServerStarted(@NotNull CompileContext context) throws Exception {
    ExternalJavacManager server = ExternalJavacManager.KEY.get(context);
    if (server != null) {
      return server;
    }
    final int listenPort = findFreePort();
    server = new ExternalJavacManager(Utils.getSystemRoot()) {
      protected ExternalJavacProcessHandler createProcessHandler(Process process) {
        return new ExternalJavacProcessHandler(process) {
          protected Future<?> executeOnPooledThread(Runnable task) {
            return SharedThreadPool.getInstance().executeOnPooledThread(task);
          }
        };
      }
    };
    server.start(listenPort);
    ExternalJavacManager.KEY.set(context, server);
    return server;
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
      return ExternalJavacManager.DEFAULT_SERVER_PORT;
    }
  }

  private static final Key<List<String>> JAVAC_OPTIONS = Key.create("_javac_options_");
  private static final Key<List<String>> JAVAC_VM_OPTIONS = Key.create("_javac_vm_options_");
  private static final Key<String> USER_DEFINED_BYTECODE_TARGET = Key.create("_user_defined_bytecode_target_");

  private static List<String> getCompilationVMOptions(CompileContext context, JavaCompilingTool compilingTool) {
    List<String> cached = JAVAC_VM_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context, compilingTool);
      cached = JAVAC_VM_OPTIONS.get(context);
    }
    return cached;
  }

  private static List<String> getCompilationOptions(CompileContext context,
                                                    ModuleChunk chunk,
                                                    @Nullable ProcessorConfigProfile profile,
                                                    @NotNull JavaCompilingTool compilingTool) {
    List<String> cached = JAVAC_OPTIONS.get(context);
    if (cached == null) {
      loadCommonJavacOptions(context, compilingTool);
      cached = JAVAC_OPTIONS.get(context);
      assert cached != null : context;
    }

    List<String> options = new ArrayList<String>();
    JpsModule module = chunk.representativeTarget().getModule();
    File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
    if (baseDirectory != null) {
      //this is a temporary workaround to allow passing per-module compiler options for Eclipse compiler in form
      // -properties $MODULE_DIR$/.settings/org.eclipse.jdt.core.prefs
      String stringToReplace = "$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$";
      String moduleDirPath = FileUtil.toCanonicalPath(baseDirectory.getAbsolutePath());
      for (String s : cached) {
        options.add(StringUtil.replace(s, stringToReplace, moduleDirPath));
      }
    }
    else {
      options.addAll(cached);
    }
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

    final JpsJavaCompilerConfiguration compilerConfiguration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      context.getProjectDescriptor().getProject()
    );

    String bytecodeTarget = null;
    for (JpsModule module : chunk.getModules()) {
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
      if (!StringUtil.isEmpty(langLevel)) {
        // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
        bytecodeTarget = langLevel;
      }
      else {
        // last resort and backward compatibility: 
        // check if user explicitly defined bytecode target in additional compiler options
        bytecodeTarget = USER_DEFINED_BYTECODE_TARGET.get(context);
      }
    }

    final int compilerSdkVersion = getCompilerSdkVersion(context);
    final int chunkSdkVersion = getChunkSdkVersion(chunk);
    
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
    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    return level != null ? level.getComplianceOption() : null;
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

  private static int getChunkSdkVersion(ModuleChunk chunk) {
    int chunkSdkVersion = -1;
    for (JpsModule module : chunk.getModules()) {
      final JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null) {
        final int moduleSdkVersion = convertToNumber(sdk.getVersionString());
        if (moduleSdkVersion != 0 /*could determine the version*/&& (chunkSdkVersion < 0 || chunkSdkVersion > moduleSdkVersion)) {
          chunkSdkVersion = moduleSdkVersion;
        }
      }
    }
    return chunkSdkVersion;
  }

  private static String getChunkSdkHome(ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      final JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null) {
        return sdk.getHomePath();
      }
    }
    return null;
  }

  private static void loadCommonJavacOptions(@NotNull CompileContext context, @NotNull JavaCompilingTool compilingTool) {
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

    compilingTool.processCompilerOptions(context, options);

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
        if (line.startsWith(ExternalJavacManager.STDOUT_LINE_PREFIX)) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println(line);
        }
        else if (line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX)) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println(line);
        }
        else if (line.contains("java.lang.OutOfMemoryError")) {
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
          kind = BuildMessage.Kind.WARNING;
          myWarningCount++;
          break;
        case NOTE:
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
