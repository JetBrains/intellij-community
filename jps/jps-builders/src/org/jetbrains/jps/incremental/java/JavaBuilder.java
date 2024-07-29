// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl;
import org.jetbrains.jps.builders.java.*;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.javac.*;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class JavaBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(JavaBuilder.class);
  private static final String JAVA_EXTENSION = "java";

  private static final String USE_MODULE_PATH_ONLY_OPTION = "compiler.force.module.path";

  public static final String BUILDER_ID = "java";
  public static final Key<Boolean> IS_ENABLED = Key.create("_java_compiler_enabled_");
  public static final FileFilter JAVA_SOURCES_FILTER = FileFilters.withExtension(JAVA_EXTENSION);

  private static final Key<Boolean> PREFER_TARGET_JDK_COMPILER = GlobalContextKey.create("_prefer_target_jdk_javac_");
  private static final Key<Set<String>> SHOWN_NOTIFICATIONS = GlobalContextKey.create("_shown_notifications_");
  private static final Key<JavaCompilingTool> COMPILING_TOOL = Key.create("_java_compiling_tool_");
  private static final Key<ConcurrentMap<String, Collection<String>>> COMPILER_USAGE_STATISTICS = Key.create("_java_compiler_usage_stats_");
  private static final Key<ModulePathSplitter> MODULE_PATH_SPLITTER = GlobalContextKey.create("_module_path_splitter_");
  private static final List<String> COMPILABLE_EXTENSIONS = Collections.singletonList(JAVA_EXTENSION);

  private static final String PROC_ONLY_OPTION = "-proc:only";
  private static final String RELEASE_OPTION = "--release";
  private static final String TARGET_OPTION = "-target";
  private static final String PROC_NONE_OPTION = "-proc:none";
  private static final String PROCESSORPATH_OPTION = "-processorpath";
  private static final String ENCODING_OPTION = "-encoding";
  private static final String ENABLE_PREVIEW_OPTION = "--enable-preview";
  private static final String PROCESSOR_MODULE_PATH_OPTION = "--processor-module-path";
  private static final String SOURCE_OPTION = "-source";
  private static final String SYSTEM_OPTION = "--system";
  private static final Set<String> FILTERED_OPTIONS = Set.of(
    TARGET_OPTION, RELEASE_OPTION, "-d"
  );
  private static final Set<String> FILTERED_SINGLE_OPTIONS = Set.of(
    "-g", "-deprecation", "-nowarn", "-verbose", PROC_NONE_OPTION, PROC_ONLY_OPTION, "-proceedOnError"
  );
  private static final Set<String> POSSIBLY_CONFLICTING_OPTIONS = Set.of(
    SOURCE_OPTION, SYSTEM_OPTION, "--boot-class-path", "-bootclasspath", "--class-path", "-classpath", "-cp", PROCESSORPATH_OPTION, "-sourcepath", "--module-path", "-p", "--module-source-path"
  );

  private static final List<ClassPostProcessor> ourClassProcessors = new ArrayList<>();
  private static final @Nullable File ourDefaultRtJar;
  static {
    File rtJar = null;
    StringTokenizer tokenizer = new StringTokenizer(System.getProperty("sun.boot.class.path", ""), File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      File file = new File(tokenizer.nextToken());
      if ("rt.jar".equals(file.getName())) {
        rtJar = file;
        break;
      }
    }
    ourDefaultRtJar = rtJar;
  }

  private static final class CompilableModuleTypesHolder {
    // avoid loading extensions on JavaBuilder.class load. Init compilable types atomically on demand
    static final Set<JpsModuleType<?>> ourCompilableModuleTypes = new HashSet<>();
    static {
      for (JavaBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(JavaBuilderExtension.class)) {
        ourCompilableModuleTypes.addAll(extension.getCompilableModuleTypes());
      }
    }
  }

  public static void registerClassPostProcessor(ClassPostProcessor processor) {
    ourClassProcessors.add(processor);
  }

  private final Executor myTaskRunner;
  private final Collection<JavacFileReferencesRegistrar> myRefRegistrars = new ArrayList<>();

  public JavaBuilder(Executor tasksExecutor) {
    super(BuilderCategory.TRANSLATOR);
    myTaskRunner = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("JavaBuilder Pool", tasksExecutor);
    //add here class processors in the sequence they should be executed
  }

  public static @NotNull @NlsSafe String getBuilderName() {
    return "java";
  }

  @Override
  public @NotNull String getPresentableName() {
    return StringUtil.capitalize(getBuilderName());
  }

  @Override
  public void buildStarted(CompileContext context) {
    final String compilerId = getUsedCompilerId(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Java compiler ID: " + compilerId);
    }
    MODULE_PATH_SPLITTER.set(context, new ModulePathSplitter(new ExplodedModuleNameFinder(context)));
    JavaCompilingTool compilingTool = JavaBuilderUtil.findCompilingTool(compilerId);
    COMPILING_TOOL.set(context, compilingTool);
    SHOWN_NOTIFICATIONS.set(context, Collections.synchronizedSet(new HashSet<>()));
    COMPILER_USAGE_STATISTICS.set(context, new ConcurrentHashMap<>());
    BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    if (!isJavac(compilingTool)) {
      dataManager.setProcessConstantsIncrementally(false);
    }
    JavaBackwardReferenceIndexWriter.initialize(context);
    for (JavacFileReferencesRegistrar registrar : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
      if (registrar.isEnabled()) {
        registrar.initialize();
        myRefRegistrars.add(registrar);
      }
    }
  }

  @Override
  public void chunkBuildStarted(final CompileContext context, final ModuleChunk chunk) {
    // before the first compilation round starts: find and mark dirty all classes that depend on removed or moved classes so
    // that all such files are compiled in the first round.
    try {
      JavaBuilderUtil.markDirtyDependenciesForInitialRound(context, new DirtyFilesHolderBase<>(context) {
        @Override
        public void processDirtyFiles(@NotNull FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget> processor) throws IOException {
          FSOperations.processFilesToRecompile(context, chunk, processor);
        }
      }, chunk);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void buildFinished(CompileContext context) {
    myRefRegistrars.clear();
    final ConcurrentMap<String, Collection<String>> stats = COMPILER_USAGE_STATISTICS.get(context);
    if (stats.size() == 1) {
      final Map.Entry<String, Collection<String>> entry = stats.entrySet().iterator().next();
      final String compilerName = entry.getKey();
      context.processMessage(new CompilerMessage("", BuildMessage.Kind.JPS_INFO,
                                                 JpsBuildBundle.message("build.message.0.was.used.to.compile.java.sources", compilerName)));
      LOG.info(compilerName + " was used to compile " + entry.getValue());
    }
    else {
      for (Map.Entry<String, Collection<String>> entry : stats.entrySet()) {
        final String compilerName = entry.getKey();
        final Collection<String> moduleNames = entry.getValue();
        context.processMessage(new CompilerMessage("", BuildMessage.Kind.JPS_INFO,
          moduleNames.size() == 1 ?
          JpsBuildBundle.message("build.message.0.was.used.to.compile.1", compilerName, moduleNames.iterator().next()) :
          JpsBuildBundle.message("build.message.0.was.used.to.compile.1.modules", compilerName, moduleNames.size())
        ));
        LOG.info(compilerName + " was used to compile " + moduleNames);
      }
    }
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return COMPILABLE_EXTENSIONS;
  }

  @Override
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
                          @NotNull OutputConsumer outputConsumer,
                          @NotNull JavaCompilingTool compilingTool) throws ProjectBuildException, IOException {
    try {
      Set<File> filesToCompile = FileCollectionFactory.createCanonicalFileLinkedSet();
      dirtyFilesHolder.processDirtyFiles((target, file, descriptor) -> {
        if (JAVA_SOURCES_FILTER.accept(file) && CompilableModuleTypesHolder.ourCompilableModuleTypes.contains(target.getModule().getModuleType())) {
          filesToCompile.add(file);
        }
        return true;
      });

      File moduleInfoFile = null;
      int javaModulesCount = 0;
      if ((!filesToCompile.isEmpty() || dirtyFilesHolder.hasRemovedFiles()) &&
          getTargetPlatformLanguageVersion(chunk.representativeTarget().getModule()) >= 9) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          final File moduleInfo = JavaBuilderUtil.findModuleInfoFile(context, target);
          if (moduleInfo != null) {
            javaModulesCount++;
            if (moduleInfoFile == null) {
              moduleInfoFile = moduleInfo;
            }
          }
        }
      }

      if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
        ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        if (logger.isEnabled() && !filesToCompile.isEmpty()) {
          logger.logCompiledFiles(filesToCompile, BUILDER_ID, "Compiling files:");
        }
      }

      if (javaModulesCount > 1) {
        @NlsSafe String modules = chunk.getModules().stream().map(JpsModule::getName).collect(Collectors.joining(", "));
        context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR,
                                                   JpsBuildBundle.message("build.message.cannot.compile.a.module.cycle.with.multiple.module.info.files", modules)));
        return ExitCode.ABORT;
      }

      return compile(context, chunk, dirtyFilesHolder, filesToCompile, outputConsumer, compilingTool, moduleInfoFile);
    }
    catch (BuildDataCorruptedException | CorruptedException | ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      String message = e.getMessage();
      if (message == null || message.trim().isEmpty()) {
        message = JpsBuildBundle.message("build.message.internal.error.0", ExceptionUtil.getThrowableText(e));
      }
      context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, message));
      throw new StopBuildException();
    }
  }

  private ExitCode compile(CompileContext context,
                           ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                           Collection<? extends File> files,
                           OutputConsumer outputConsumer,
                           JavaCompilingTool compilingTool,
                           File moduleInfoFile) throws Exception {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final boolean hasSourcesToCompile = !files.isEmpty();

    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return exitCode;
    }

    final ProjectDescriptor pd = context.getProjectDescriptor();

    JavaBuilderUtil.ensureModuleHasJdk(chunk.representativeTarget().getModule(), context, getBuilderName());
    final Collection<File> classpath = ProjectPaths.getCompilationClasspath(chunk, false);
    final Collection<File> platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false);

    // begin compilation round
    final OutputFilesSink outputSink =
      new OutputFilesSink(context, outputConsumer, JavaBuilderUtil.getDependenciesRegistrar(context), chunk.getPresentableShortName());
    Collection<File> filesWithErrors = null;
    try {
      if (hasSourcesToCompile) {
        exitCode = ExitCode.OK;

        final Set<File> srcPath = new HashSet<>();
        final BuildRootIndex index = pd.getBuildRootIndex();
        for (ModuleBuildTarget target : chunk.getTargets()) {
          for (JavaSourceRootDescriptor rd : index.getTempTargetRoots(target, context)) {
            srcPath.add(rd.root);
          }
        }
        final DiagnosticSink diagnosticSink = new DiagnosticSink(context, Collections.unmodifiableCollection(myRefRegistrars));

        final String chunkName = chunk.getName();
        context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.parsing.java.0", chunk.getPresentableShortName())));

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
            compiledOk = compileJava(context, chunk, files, classpath, platformCp, srcPath, diagnosticSink, outputSink, compilingTool, moduleInfoFile);
          }
          finally {
            filesWithErrors = diagnosticSink.getFilesWithErrors();
          }
        }

        context.checkCanceled();

        if (!compiledOk && diagnosticSink.getErrorCount() == 0) {
          // unexpected exception occurred or compiler did not output any errors for some reason
          diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, JpsBuildBundle.message("build.message.compilation.failed.internal.java.compiler.error")));
        }

        if (!Utils.PROCEED_ON_ERROR_KEY.get(context, Boolean.FALSE) && diagnosticSink.getErrorCount() > 0) {
          throw new StopBuildException(
            JpsBuildBundle.message("build.message.compilation.failed.errors.0.warnings.1", diagnosticSink.getErrorCount(), diagnosticSink.getWarningCount())
          );
        }
      }
    }
    finally {
      JavaBuilderUtil.registerFilesToCompile(context, files);
      if (filesWithErrors != null) {
        JavaBuilderUtil.registerFilesWithErrors(context, filesWithErrors);
      }
      JavaBuilderUtil.registerSuccessfullyCompiled(context, outputSink.getSuccessfullyCompiled());
    }

    return exitCode;
  }

  private boolean compileJava(CompileContext context,
                              ModuleChunk chunk,
                              Collection<? extends File> files,
                              Collection<? extends File> originalClassPath,
                              Collection<? extends File> originalPlatformCp,
                              Collection<? extends File> sourcePath,
                              DiagnosticOutputConsumer diagnosticSink,
                              OutputFileConsumer outputSink,
                              JavaCompilingTool compilingTool,
                              File moduleInfoFile) throws IOException {
    final Semaphore counter = new Semaphore();
    COUNTER_KEY.set(context, counter);

    final Set<JpsModule> modules = chunk.getModules();
    ProcessorConfigProfile profile = null;

    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(
      context.getProjectDescriptor().getProject()
    );

    if (modules.size() == 1) {
      profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next());
    }
    else {
      final String message = validateCycle(context, chunk);
      if (message != null) {
        diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
        return false;
      }
    }

    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    try {
      final int targetLanguageLevel = getTargetPlatformLanguageVersion(chunk.representativeTarget().getModule());

      // when forking external javac, compilers from SDK 1.7 and higher are supported
      final Pair<String, Integer> forkSdk;
      if (shouldForkCompilerProcess(context, chunk, targetLanguageLevel, compilingTool)) {
        forkSdk = getForkedJavacSdk(diagnosticSink, chunk, targetLanguageLevel);
        if (forkSdk == null) {
          return false;
        }
      }
      else {
        forkSdk = null;
      }

      final int compilerSdkVersion = forkSdk == null ? JavaVersion.current().feature : forkSdk.getSecond();

      final Pair<Iterable<String>, Iterable<String>> vm_compilerOptions = getCompilationOptions(compilerSdkVersion, context, chunk, profile, compilingTool);
      final Iterable<String> vmOptions = vm_compilerOptions.first;
      final Iterable<String> options = vm_compilerOptions.second;

      Iterable<? extends File> effectivePlatformCp = calcEffectivePlatformCp(originalPlatformCp, options, compilingTool);
      if (effectivePlatformCp == null) {
        String text = JpsBuildBundle.message(
          "build.message.unsupported.compact.compilation.profile.was.requested", chunk.getName(), System.getProperty("java.version")
        );
        context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, text));
        return false;
      }

      final Iterable<? extends File> platformCp;
      final Iterable<? extends File> classPath;
      final ModulePath modulePath;
      final Iterable<? extends File> upgradeModulePath;
      if (moduleInfoFile != null) { // has modules
        final ModulePathSplitter splitter = MODULE_PATH_SPLITTER.get(context);
        final Pair<ModulePath, Collection<File>> pair = splitter.splitPath(
          moduleInfoFile, outs.keySet(), ProjectPaths.getCompilationModulePath(chunk, false), collectAdditionalRequires(options)
        );
        final boolean useModulePathOnly = Boolean.parseBoolean(System.getProperty(USE_MODULE_PATH_ONLY_OPTION))/*compilerConfig.useModulePathOnly()*/;
        if (useModulePathOnly) {
          // in Java 9, named modules are not allowed to read classes from the classpath
          // moreover, the compiler requires all transitive dependencies to be on the module path
          ModulePath.Builder mpBuilder = ModulePath.newBuilder();
          for (File file : ProjectPaths.getCompilationModulePath(chunk, false)) {
            mpBuilder.add(pair.first.getModuleName(file), file);
          }
          modulePath = mpBuilder.create();
          classPath = Collections.emptyList();
        }
        else {
          // placing only explicitly referenced modules into the module path and the rest of deps to classpath
          modulePath = pair.first;
          classPath = pair.second;
        }
        // modules above the JDK in the order entry list make a module upgrade path
        upgradeModulePath = effectivePlatformCp;
        platformCp = Collections.emptyList();
      }
      else {
        modulePath = ModulePath.EMPTY;
        upgradeModulePath = Collections.emptyList();
        if (!Iterators.isEmpty(effectivePlatformCp) && (getChunkSdkVersion(chunk)) >= 9) {
          // if chunk's SDK is 9 or higher, there is no way to specify full platform classpath
          // because platform classes are stored in jimage binary files with unknown format.
          // Because of this we are clearing platform classpath so that javac will resolve against its own boot classpath
          // and prepending additional jars from the JDK configuration to compilation classpath
          platformCp = Collections.emptyList();
          classPath = Iterators.flat(effectivePlatformCp, originalClassPath);
        }
        else {
          platformCp = effectivePlatformCp;
          classPath = originalClassPath;
        }
      }

      final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);

      if (forkSdk != null) {
        updateCompilerUsageStatistics(context, "javac " + forkSdk.getSecond(), chunk);
        final ExternalJavacManager server = ensureJavacServerStarted(context);
        final CompilationPaths paths = CompilationPaths.create(platformCp, classPath, upgradeModulePath, modulePath, sourcePath);
        final int heapSize = Utils.suggestForkedCompilerHeapSize();
        return invokeJavac(compilerSdkVersion, context, chunk, compilingTool, options, files, classesConsumer, (_options, _files, _outSink) -> {
          logJavacCall(chunk, _options, "fork");
          return server.forkJavac(
            forkSdk.getFirst(), heapSize, vmOptions, _options, paths, _files, outs, diagnosticSink, _outSink, compilingTool, context.getCancelStatus(), true
          ).get();
        });
      }

      updateCompilerUsageStatistics(context, compilingTool.getDescription(), chunk);
      return invokeJavac(compilerSdkVersion, context, chunk, compilingTool, options, files, classesConsumer, (_options, _files, _outSink) -> {
        logJavacCall(chunk, _options, "in-process");
        return JavacMain.compile(
          _options, _files, classPath, platformCp, modulePath, upgradeModulePath, sourcePath, outs, diagnosticSink, _outSink, context.getCancelStatus(), compilingTool
        );
      });
    }
    finally {
      counter.waitFor();
    }
  }

  private static @NotNull Collection<String> collectAdditionalRequires(Iterable<String> options) {
    // --add-reads module=other-module(,other-module)*
    // The option specifies additional modules to be considered as required by a given module.
    final Set<String> result = new SmartHashSet<>();
    for (Iterator<String> it = options.iterator(); it.hasNext(); ) {
      final String option = it.next();
      if ("--add-reads".equalsIgnoreCase(option) && it.hasNext()) {
        final String moduleNames = StringUtil.substringAfter(it.next(), "=");
        if (moduleNames != null) {
          result.addAll(StringUtil.split(moduleNames, ","));
        }
      }
    }
    return result;
  }

  private static void logJavacCall(ModuleChunk chunk, Iterable<String> options, final String mode) {
    if (LOG.isDebugEnabled()) {
      LOG.debug((Iterators.contains(options, PROC_ONLY_OPTION)? "Running processors for chunk" : "Compiling chunk") + " [" + chunk.getName() + "] with options: \"" + StringUtil.join(options, " ") + "\", mode=" + mode);
    }
  }

  private interface JavacCaller {
    boolean invoke(Iterable<String> options, Iterable<? extends File> files, OutputFileConsumer outSink);
  }
  private static boolean invokeJavac(
    int compilerSdkVersion,
    CompileContext context,
    ModuleChunk chunk,
    @NotNull JavaCompilingTool compilingTool,
    Iterable<String> options,
    Iterable<? extends File> files,
    OutputFileConsumer outSink,
    JavacCaller javacCall
  ) {
    if (Iterators.contains(options, PROC_ONLY_OPTION)) {
      // make a dedicated javac call for annotation processing only
      final Collection<File> generated = new ArrayList<>();
      final boolean processingSuccess = javacCall.invoke(options, files, fileObject -> {
        if (fileObject.getKind() == JavaFileObject.Kind.SOURCE) {
          generated.add(fileObject.getFile());
        }
        outSink.save(fileObject);
      });
      if (!processingSuccess) {
        return false;
      }
      // now call javac with processor-generated sources and without processing-related options
      final Iterable<String> compileOnlyOptions = getCompilationOptions(compilerSdkVersion, context, chunk, null, compilingTool).second;
      return javacCall.invoke(compileOnlyOptions, Iterators.flat(files, generated), outSink);
    }

    return javacCall.invoke(options, files, outSink);
  }

  private static void updateCompilerUsageStatistics(CompileContext context, String compilerName, ModuleChunk chunk) {
    final ConcurrentMap<String, Collection<String>> map = COMPILER_USAGE_STATISTICS.get(context);
    Collection<String> names = map.get(compilerName);
    if (names == null) {
      names = Collections.synchronizedSet(new HashSet<>());
      final Collection<String> prev = map.putIfAbsent(compilerName, names);
      if (prev != null) {
        names = prev;
      }
    }
    for (JpsModule module : chunk.getModules()) {
      names.add(module.getName());
    }
  }

  public static @Nullable @Nls String validateCycle(CompileContext context, ModuleChunk chunk) {
    final JpsJavaExtensionService javaExt = JpsJavaExtensionService.getInstance();
    final JpsJavaCompilerConfiguration compilerConfig = javaExt.getCompilerConfiguration(context.getProjectDescriptor().getProject());
    final Set<JpsModule> modules = chunk.getModules();
    Pair<String, LanguageLevel> pair = null;
    for (JpsModule module : modules) {
      final LanguageLevel moduleLevel = javaExt.getLanguageLevel(module);
      if (pair == null) {
        pair = Pair.create(module.getName(), moduleLevel); // first value
      }
      else if (!Comparing.equal(pair.getSecond(), moduleLevel)) {
        return JpsBuildBundle.message("build.message.modules.0.and.1.must.have.the.same.language.level", pair.getFirst(), module.getName());
      }
    }

    final JpsJavaCompilerOptions compilerOptions = compilerConfig.getCurrentCompilerOptions();
    final Map<String, String> overrideMap = compilerOptions.ADDITIONAL_OPTIONS_OVERRIDE;
    if (!overrideMap.isEmpty()) {
      // check that options are consistently overridden for all modules in the cycle
      Pair<String, Set<String>> overridden = null;
      for (JpsModule module : modules) {
        final String opts = overrideMap.get(module.getName());
        if (!StringUtil.isEmptyOrSpaces(opts)) {
          final Set<String> parsed = parseOptions(opts);
          if (overridden == null) {
            overridden = Pair.create(module.getName(), parsed);
          }
          else {
            if (!overridden.second.equals(parsed)) {
              return JpsBuildBundle.message("build.message.modules.0.and.1.must.have.the.same.additional.command.line.parameters", overridden.first, module.getName());
            }
          }
        }
        else {
          context.processMessage(new CompilerMessage(
            getBuilderName(), BuildMessage.Kind.WARNING,
            JpsBuildBundle.message("build.message.some.modules.with.cyclic.dependencies.0.have.additional.command.line.parameters", chunk.getName())
          ));
        }
      }
    }

    // check that all chunk modules are excluded from annotation processing
    for (JpsModule module : modules) {
      final ProcessorConfigProfile prof = compilerConfig.getAnnotationProcessingProfile(module);
      if (prof.isEnabled()) {
        return JpsBuildBundle.message("build.message.annotation.processing.is.not.supported.for.module.cycles", chunk.getName());
      }
    }
    return null;
  }

  private static Set<String> parseOptions(String str) {
    final Set<String> result = new SmartHashSet<>();
    StringTokenizer t = new StringTokenizer(str, " \n\t", false);
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }
    return result;
  }

  private static boolean shouldUseReleaseOption(JpsJavaCompilerConfiguration config, int compilerVersion, int chunkSdkVersion, int targetPlatformVersion) {
    if (!config.useReleaseOption()) {
      return false;
    }
    // --release option is supported in java9+ and higher
    if (compilerVersion >= 9 && chunkSdkVersion > 0 && targetPlatformVersion > 0) {
      if (chunkSdkVersion < 9) {
        // target sdk is set explicitly and differs from compiler SDK, so for consistency we should link against it
        return false;
      }
      // chunkSdkVersion >= 9, so we have no rt.jar anymore and '-release' is the only cross-compilation option available
      // Only specify '--release' when cross-compilation is indeed really required.
      // Otherwise '--release' may not be compatible with other compilation options, e.g. exporting a package from system module
      return compilerVersion != targetPlatformVersion;
    }
    return false;
  }

  private static boolean shouldForkCompilerProcess(CompileContext context, ModuleChunk chunk, int chunkLanguageLevel, JavaCompilingTool compilingTool) {
    if (!isJavac(compilingTool)) {
      return false; // applicable to javac only
    }
    final int compilerSdkVersion = JavaVersion.current().feature;

    if (preferTargetJdkCompiler(context)) {
      final Pair<JpsSdk<JpsDummyElement>, Integer> sdkVersionPair = getAssociatedSdk(chunk);
      if (sdkVersionPair != null) {
        final Integer chunkSdkVersion = sdkVersionPair.second;
        if (chunkSdkVersion != compilerSdkVersion && chunkSdkVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION) {
          // there is a special case because of difference in type inference behavior between javac8 and javac6-javac7
          // so if corresponding JDK is associated with the module chunk, prefer compiler from this JDK over the newer compiler version
          return true;
        }
      }
    }

    if (chunkLanguageLevel <= 0) {
      // was not able to determine jdk version, so assuming in-process compiler
      return false;
    }
    return !isTargetReleaseSupported(compilerSdkVersion, chunkLanguageLevel);
  }

  private static boolean isTargetReleaseSupported(int compilerVersion, int targetPlatformVersion) {
    if (targetPlatformVersion > compilerVersion) {
      return false;
    }
    if (compilerVersion < 9) {
      return true;
    }
    if (compilerVersion <= 11) {
      return targetPlatformVersion >= 6;
    }
    if (compilerVersion <= 19) {
      return targetPlatformVersion >= 7;
    }
    return targetPlatformVersion >= 8;
  }

  private static boolean isJavac(final JavaCompilingTool compilingTool) {
    return compilingTool != null && (compilingTool.getId().equals(JavaCompilers.JAVAC_ID) ||
                                     compilingTool.getId().equals(JavaCompilers.JAVAC_API_ID));
  }

  private static boolean preferTargetJdkCompiler(CompileContext context) {
    Boolean val = PREFER_TARGET_JDK_COMPILER.get(context);
    if (val == null) {
      JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
      // default
      PREFER_TARGET_JDK_COMPILER.set(context, val = config.getCompilerOptions(JavaCompilers.JAVAC_ID).PREFER_TARGET_JDK_COMPILER);
    }
    return val;
  }

  // If platformCp of the build process is the same as the target platform, do not specify platformCp explicitly
  // this will allow javac to resolve against ct.sym file, which is required for the "compilation profiles" feature
  private static @Nullable Iterable<? extends File> calcEffectivePlatformCp(Collection<? extends File> platformCp, Iterable<String> options, JavaCompilingTool compilingTool) {
    if (ourDefaultRtJar == null || !isJavac(compilingTool)) {
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
    Semaphore counter = COUNTER_KEY.get(context);

    assert counter != null;

    counter.down();
    myTaskRunner.execute(() -> {
      try {
        taskRunnable.run();
      }
      catch (Throwable e) {
        context.processMessage(new CompilerMessage(getBuilderName(), e));
      }
      finally {
        counter.up();
      }
    });
  }

  private static synchronized @NotNull ExternalJavacManager ensureJavacServerStarted(@NotNull CompileContext context) throws IOException {
    ExternalJavacManager server = ExternalJavacManager.KEY.get(context);
    if (server != null) {
      return server;
    }
    final int listenPort = findFreePort();
    server = new ExternalJavacManager(Utils.getSystemRoot(), SharedThreadPool.getInstance(), 2 * 60 * 1000L /*keep idle builds for 2 minutes*/) {
      @Override
      protected ExternalJavacProcessHandler createProcessHandler(UUID processId, @NotNull Process process, @NotNull String commandLine, boolean keepProcessAlive) {
        return new ExternalJavacProcessHandler(processId, process, commandLine, keepProcessAlive) {
          @Override
          public @NotNull Future<?> executeTask(@NotNull Runnable task) {
            return SharedThreadPool.getInstance().submit(task);
          }

          @Override
          protected @NotNull BaseOutputReader.Options readerOptions() {
            return BaseOutputReader.Options.NON_BLOCKING;
          }
        };
      }
    };
    server.start(listenPort);
    ExternalJavacManager.KEY.set(context, server);
    return server;
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

  private static final Key<String> USER_DEFINED_BYTECODE_TARGET = Key.create("_user_defined_bytecode_target_");

  private static Pair<Iterable<String>, Iterable<String>> getCompilationOptions(int compilerSdkVersion,
                                                                        CompileContext context,
                                                                        ModuleChunk chunk,
                                                                        @Nullable ProcessorConfigProfile profile,
                                                                        @NotNull JavaCompilingTool compilingTool) {
    final List<String> compilationOptions = new ArrayList<>();
    final List<String> vmOptions = new ArrayList<>();
    if (!JavacMain.TRACK_AP_GENERATED_DEPENDENCIES) {
      vmOptions.add("-D" + JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY + "=" + JavacMain.TRACK_AP_GENERATED_DEPENDENCIES);
      notifyMessage(context, BuildMessage.Kind.WARNING, "build.message.incremental.annotation.processing.disabled.0", true, JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY);
    }
    if (compilerSdkVersion > 15) {
      // enable javac-related reflection tricks in JPS
      ClasspathBootstrap.configureReflectionOpenPackages(p -> vmOptions.add(p));
    }
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerOptions compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).getCurrentCompilerOptions();
    if (compilerOptions.DEBUGGING_INFO) {
      compilationOptions.add("-g");
    }
    if (compilerOptions.DEPRECATION) {
      compilationOptions.add("-deprecation");
    }
    if (compilerOptions.GENERATE_NO_WARNINGS) {
      compilationOptions.add("-nowarn");
    }
    if (compilerOptions instanceof EclipseCompilerOptions) {
      final EclipseCompilerOptions eclipseOptions = (EclipseCompilerOptions)compilerOptions;
      if (eclipseOptions.PROCEED_ON_ERROR) {
        Utils.PROCEED_ON_ERROR_KEY.set(context, Boolean.TRUE);
        compilationOptions.add("-proceedOnError");
      }
    }

    String customArgs = compilerOptions.ADDITIONAL_OPTIONS_STRING;
    final Map<String, String> overrideMap = compilerOptions.ADDITIONAL_OPTIONS_OVERRIDE;
    if (!overrideMap.isEmpty()) {
      for (JpsModule m : chunk.getModules()) {
        final String overridden = overrideMap.get(m.getName());
        if (overridden != null) {
          customArgs = overridden;
          break;
        }
      }
    }

    if (customArgs != null && !customArgs.isEmpty()) {
      BiConsumer<List<String>, String> appender = List::add;
      final JpsModule module = chunk.representativeTarget().getModule();
      final File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
      if (baseDirectory != null) {
        //this is a temporary workaround to allow passing per-module compiler options for Eclipse compiler in form
        // -properties $MODULE_DIR$/.settings/org.eclipse.jdt.core.prefs
        final String moduleDirPath = FileUtil.toCanonicalPath(baseDirectory.getAbsolutePath());
        appender = (strings, option) -> strings.add(StringUtil.replace(option, PathMacroUtil.DEPRECATED_MODULE_DIR, moduleDirPath));
      }

      boolean skip = false;
      boolean targetOptionFound = false;
      for (final String userOption : ParametersListUtil.parse(customArgs)) {
        if (FILTERED_OPTIONS.contains(userOption)) {
          skip = true;
          targetOptionFound = TARGET_OPTION.equals(userOption);
          notifyOptionIgnored(context, userOption, chunk);
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
            if (POSSIBLY_CONFLICTING_OPTIONS.contains(userOption)) {
              notifyOptionPossibleConflicts(context, userOption, chunk);
            }
            if (userOption.startsWith("-J-")) {
              vmOptions.add(userOption.substring("-J".length()));
            }
            else {
              appender.accept(compilationOptions, userOption);
            }
          }
          else {
            notifyOptionIgnored(context, userOption, chunk);
          }
        }
      }
    }

    for (ExternalJavacOptionsProvider extension : JpsServiceManager.getInstance().getExtensions(ExternalJavacOptionsProvider.class)) {
      vmOptions.addAll(extension.getOptions(compilingTool, compilerSdkVersion));
    }

    addCompilationOptions(compilerSdkVersion, compilationOptions, context, chunk, profile, true);

    return Pair.create(vmOptions, compilationOptions);
  }

  private static void notifyOptionPossibleConflicts(CompileContext context, String option, ModuleChunk chunk) {
    notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.for.1.may.conflict.with.calculated.option", false, option, chunk.getPresentableShortName());
  }

  private static void notifyOptionIgnored(CompileContext context, String option, ModuleChunk chunk) {
    notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.is.ignored.for.1", false, option, chunk.getPresentableShortName());
  }

  private static void notifyMessage(CompileContext context, final BuildMessage.Kind kind, final String messageKey, boolean notifyOnce, Object... params) {
    if (!notifyOnce || SHOWN_NOTIFICATIONS.get(context).add(messageKey)) {
      context.processMessage(new CompilerMessage(getBuilderName(), kind, JpsBuildBundle.message(messageKey, params)));
    }
  }

  public static void addCompilationOptions(List<? super String> options, CompileContext context, ModuleChunk chunk, @Nullable ProcessorConfigProfile profile) {
    addCompilationOptions(JavaVersion.current().feature, options, context, chunk, profile, false);
  }

  private static void addCompilationOptions(
    int compilerSdkVersion, List<? super String> options, CompileContext context, ModuleChunk chunk, @Nullable ProcessorConfigProfile profile, boolean procOnlySupported
  ) {
    if (!options.contains(ENCODING_OPTION)) {
      final CompilerEncodingConfiguration config = context.getProjectDescriptor().getEncodingConfiguration();
      final String encoding = config.getPreferredModuleChunkEncoding(chunk);
      if (config.getAllModuleChunkEncodings(chunk).size() > 1) {
        String message = JpsBuildBundle.message("build.message.multiple.encodings.set.for.module.chunk", chunk.getName(), encoding, encoding != null ? 0 : 1);
        context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.INFO, message));
      }
      if (!StringUtil.isEmpty(encoding)) {
        options.add(ENCODING_OPTION);
        options.add(encoding);
      }
    }

    addCrossCompilationOptions(compilerSdkVersion, options, context, chunk);

    if (!options.contains(ENABLE_PREVIEW_OPTION)) {
      LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(chunk.representativeTarget().getModule());
      if (level != null && level.isPreview()) {
        options.add(ENABLE_PREVIEW_OPTION);
      }
    }

    if (addAnnotationProcessingOptions(options, profile)) {
      assert profile != null;

      if (procOnlySupported && profile.isProcOnly()) {
        options.add(PROC_ONLY_OPTION);
      }

      final File srcOutput = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(
        chunk.getModules().iterator().next(), chunk.containsTests(), profile
      );
      if (srcOutput != null) {
        FileUtil.createDirectory(srcOutput);
        options.add("-s");
        options.add(srcOutput.getPath());
      }
    }
  }

  /**
   * @return true if annotation processing is enabled and corresponding options were added, false if profile is null or disabled
   */
  public static boolean addAnnotationProcessingOptions(List<? super String> options, @Nullable AnnotationProcessingConfiguration profile) {
    if (profile == null || !profile.isEnabled()) {
      options.add(PROC_NONE_OPTION);
      return false;
    }

    // configuring annotation processing
    if (!profile.isObtainProcessorsFromClasspath()) {
      final String processorsPath = profile.getProcessorPath();
      options.add(profile.isUseProcessorModulePath() ? PROCESSOR_MODULE_PATH_OPTION : PROCESSORPATH_OPTION);
      options.add(FileUtil.toSystemDependentName(processorsPath.trim()));
    }

    final Set<String> processors = profile.getProcessors();
    if (!processors.isEmpty()) {
      options.add("-processor");
      options.add(StringUtil.join(processors, ","));
    }

    for (Map.Entry<String, String> optionEntry : profile.getProcessorOptions().entrySet()) {
      options.add("-A" + optionEntry.getKey() + "=" + optionEntry.getValue());
    }
    return true;
  }

  public static @NotNull String getUsedCompilerId(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    return JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).getJavaCompilerId();
  }

  private static void addCrossCompilationOptions(int compilerSdkVersion, List<? super String> options, CompileContext context, ModuleChunk chunk) {
    final JpsJavaCompilerConfiguration compilerConfiguration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(
      context.getProjectDescriptor().getProject()
    );

    @NotNull JpsModule module = chunk.representativeTarget().getModule();
    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    final int chunkSdkVersion = getChunkSdkVersion(chunk);
    final int languageLevel = level == null? 0 : level == LanguageLevel.JDK_X? chunkSdkVersion : level.feature();
    int bytecodeTarget = level == LanguageLevel.JDK_X? chunkSdkVersion : getModuleBytecodeTarget(context, chunk, compilerConfiguration, languageLevel);

    if (shouldUseReleaseOption(compilerConfiguration, compilerSdkVersion, chunkSdkVersion, bytecodeTarget)) {
      options.add(RELEASE_OPTION);
      options.add(complianceOption(bytecodeTarget));
      return;
    }

    // alternatively using -source, -target and --system (or -bootclasspath) options
    if (languageLevel > 0 && !options.contains(SOURCE_OPTION)) {
      options.add(SOURCE_OPTION);
      options.add(complianceOption(languageLevel));
    }

    if (bytecodeTarget > 0) {
      if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
        // if compiler is newer than module JDK
        if (compilerSdkVersion >= bytecodeTarget) {
          // if user-specified bytecode version can be determined and is supported by compiler
          if (bytecodeTarget > chunkSdkVersion) {
            // and user-specified bytecode target level is higher than the highest one supported by the target JDK,
            // force compiler to use highest-available bytecode target version that is supported by the chunk JDK.
            bytecodeTarget = chunkSdkVersion;
          }
        }
        // otherwise let compiler display compilation error about incorrectly set bytecode target version
      }
    }
    else {
      if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
        // force lower bytecode target level to match the version of the chunk JDK
        bytecodeTarget = chunkSdkVersion;
      }
    }

    if (bytecodeTarget > 0) {
      options.add(TARGET_OPTION);
      options.add(complianceOption(bytecodeTarget));
    }

    if (compilerSdkVersion >= 9) {
      Pair<@NotNull JpsSdk<JpsDummyElement>, @NotNull Integer> associatedSdk = getAssociatedSdk(chunk);
      if (associatedSdk != null && associatedSdk.getSecond() >= 9 && associatedSdk.getSecond() != compilerSdkVersion) {
        String homePath = associatedSdk.getFirst().getHomePath();
        if (homePath != null) {
          options.add(SYSTEM_OPTION);
          options.add(FileUtil.toSystemIndependentName(homePath));
        }
      }
    }
  }

  public static int getModuleBytecodeTarget(CompileContext context, ModuleChunk chunk, JpsJavaCompilerConfiguration compilerConfiguration) {
    return getModuleBytecodeTarget(context, chunk, compilerConfiguration, getLanguageLevel(chunk.representativeTarget().getModule()));
  }

  private static int getModuleBytecodeTarget(CompileContext context, ModuleChunk chunk, JpsJavaCompilerConfiguration compilerConfiguration, int languageLevel) {
    int bytecodeTarget = 0;
    for (JpsModule module : chunk.getModules()) {
      // use the lower possible target among modules that form the chunk
      final int moduleTarget = JpsJavaSdkType.parseVersion(compilerConfiguration.getByteCodeTargetLevel(module.getName()));
      if (moduleTarget > 0 && (bytecodeTarget == 0 || moduleTarget < bytecodeTarget)) {
        bytecodeTarget = moduleTarget;
      }
    }
    if (bytecodeTarget == 0) {
      if (languageLevel > 0) {
        // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
        bytecodeTarget = languageLevel;
      }
      else {
        // last resort and backward compatibility:
        // check if user explicitly defined bytecode target in additional compiler options
        String value = USER_DEFINED_BYTECODE_TARGET.get(context);
        if (value != null) {
          bytecodeTarget = JpsJavaSdkType.parseVersion(value);
        }
      }
    }
    return bytecodeTarget;
  }

  private static String complianceOption(int major) {
    return JpsJavaSdkType.complianceOption(JavaVersion.compose(major));
  }

  private static int getLanguageLevel(@NotNull JpsModule module) {
    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    return level != null ? level.feature() : 0;
  }

  /**
   * The assumed module's source code language version.
   * Returns the version number, corresponding to the language level, associated with the given module.
   * If no language level set (neither on module- nor on project-level), the version of JDK associated with the module is returned.
   * If no JDK is associated, returns 0.
   */
  private static int getTargetPlatformLanguageVersion(@NotNull JpsModule module) {
    final int level = getLanguageLevel(module);
    if (level > 0) {
      return level;
    }
    // when compiling, if language level is not explicitly set, it is assumed to be equal to
    // the highest possible language level supported by target JDK
    final JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
    if (sdk != null) {
      return JpsJavaSdkType.getJavaVersion(sdk);
    }
    return 0;
  }

  private static int getChunkSdkVersion(ModuleChunk chunk) {
    int chunkSdkVersion = -1;
    for (JpsModule module : chunk.getModules()) {
      final JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
      if (sdk != null) {
        final int moduleSdkVersion = JpsJavaSdkType.getJavaVersion(sdk);
        if (moduleSdkVersion != 0 /*could determine the version*/ && (chunkSdkVersion < 0 || chunkSdkVersion > moduleSdkVersion)) {
          chunkSdkVersion = moduleSdkVersion;
        }
      }
    }
    return chunkSdkVersion;
  }

  private static @Nullable Pair<String, Integer> getForkedJavacSdk(DiagnosticListener<? super JavaFileObject> diagnostic, ModuleChunk chunk, int targetLanguageLevel) {
    final Pair<JpsSdk<JpsDummyElement>, Integer> associatedSdk = getAssociatedSdk(chunk);
    boolean canRunAssociatedJavac = false;
    if (associatedSdk != null) {
      final int sdkVersion = associatedSdk.second;
      canRunAssociatedJavac = sdkVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION;
      if (isTargetReleaseSupported(sdkVersion, targetLanguageLevel)) {
        if (canRunAssociatedJavac) {
          return Pair.create(associatedSdk.first.getHomePath(), sdkVersion);
        }
      }
      else {
        LOG.warn("Target bytecode version " + targetLanguageLevel + " is not supported by SDK " + sdkVersion + " associated with module " + chunk.getName());
      }
    }

    final String fallbackJdkHome = System.getProperty(GlobalOptions.FALLBACK_JDK_HOME, null);
    if (fallbackJdkHome == null) {
      LOG.info("Fallback JDK is not specified. (See " + GlobalOptions.FALLBACK_JDK_HOME + " option)");
    }
    final String fallbackJdkVersion = System.getProperty(GlobalOptions.FALLBACK_JDK_VERSION, null);
    if (fallbackJdkVersion == null) {
      LOG.info("Fallback JDK version is not specified. (See " + GlobalOptions.FALLBACK_JDK_VERSION + " option)");
    }

    if (associatedSdk == null && (fallbackJdkHome == null || fallbackJdkVersion == null)) {
      diagnostic.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, JpsBuildBundle.message("build.message.cannot.start.javac.process.for.0.unknown.jdk.home", chunk.getName())));
      return null;
    }

    // either associatedSdk or fallbackJdk is configured, but associatedSdk cannot be used
    if (fallbackJdkHome != null) {
      final int fallbackVersion = JpsJavaSdkType.parseVersion(fallbackJdkVersion);
      if (isTargetReleaseSupported(fallbackVersion, targetLanguageLevel)) {
        if (fallbackVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION) {
          return Pair.create(fallbackJdkHome, fallbackVersion);
        }
        else {
          LOG.info("Version string for fallback JDK is '" + fallbackJdkVersion + "' (recognized as version '" + fallbackVersion + "')." +
                   " At least version " + ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION + " is required to launch javac process.");
        }
      }
    }

    // at this point, fallbackJdk is not suitable too
    if (associatedSdk != null) {
      if (canRunAssociatedJavac) {
        // although target release is not supported, attempt to start javac, so that javac properly reports this error
        return Pair.create(associatedSdk.first.getHomePath(), associatedSdk.second);
      }
      else {
        diagnostic.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR,
          JpsBuildBundle.message(
            "build.message.unsupported.javac.version",
            chunk.getName(),
            associatedSdk.second,
            ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION,
            targetLanguageLevel
          )
        ));
      }
    }

    return null;
  }

  private static @Nullable Pair<@NotNull JpsSdk<JpsDummyElement>, @NotNull Integer> getAssociatedSdk(ModuleChunk chunk) {
    // assuming all modules in the chunk have the same associated JDK;
    // this constraint should be validated on build start
    final JpsSdk<JpsDummyElement> sdk = chunk.representativeTarget().getModule().getSdk(JpsJavaSdkType.INSTANCE);
    return sdk != null ? Pair.create(sdk, JpsJavaSdkType.getJavaVersion(sdk)) : null;
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
    ExternalJavacManager extJavacManager = ExternalJavacManager.KEY.get(context);
    if (extJavacManager != null) {
      extJavacManager.shutdownIdleProcesses();
    }
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = FileCollectionFactory.createCanonicalFileMap();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      final File outputDir = target.getOutputDir();
      if (outputDir == null) {
        continue;
      }
      final Set<File> roots = FileCollectionFactory.createCanonicalFileSet();
      for (JavaSourceRootDescriptor descriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
        roots.add(descriptor.root);
      }
      map.put(outputDir, roots);
    }
    return map;
  }

  private static final class DiagnosticSink implements DiagnosticOutputConsumer {
    private final CompileContext myContext;
    private final AtomicInteger myErrorCount = new AtomicInteger(0);
    private final AtomicInteger myWarningCount = new AtomicInteger(0);
    private final Set<File> myFilesWithErrors = FileCollectionFactory.createCanonicalFileSet();
    private final @NotNull Collection<? extends JavacFileReferencesRegistrar> myRegistrars;

    private DiagnosticSink(CompileContext context, @NotNull Collection<? extends JavacFileReferencesRegistrar> refRegistrars) {
      myContext = context;
      myRegistrars = refRegistrars;
    }

    @Override
    public void javaFileLoaded(File file) {
    }

    @Override
    public void registerJavacFileData(JavacFileData data) {
      for (JavacFileReferencesRegistrar registrar : myRegistrars) {
        registrar.registerFile(myContext, data.getFilePath(), Iterators.map(data.getRefs().entrySet(), entry -> entry), data.getDefs(), data.getCasts(), data.getImplicitToStringRefs());
      }
    }

    @Override
    public void customOutputData(String pluginId, String dataName, byte[] data) {
      if (JavacFileData.CUSTOM_DATA_PLUGIN_ID.equals(pluginId) && JavacFileData.CUSTOM_DATA_KIND.equals(dataName)) {
        registerJavacFileData(JavacFileData.fromBytes(data));
      }
      else {
        for (CustomOutputDataListener listener : JpsServiceManager.getInstance().getExtensions(CustomOutputDataListener.class)) {
          if (pluginId.equals(listener.getId())) {
            listener.processData(myContext, dataName, data);
            return;
          }
        }
      }
    }

    @Override
    public void outputLineAvailable(@NlsSafe String line) {
      if (!StringUtil.isEmpty(line)) {
        if (line.startsWith(ExternalJavacManager.STDOUT_LINE_PREFIX)) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println(line);
          if (LOG.isDebugEnabled()) {
            LOG.debug(line);
          }
        }
        else if (line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX)) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println(line);
          if (LOG.isDebugEnabled()) {
            LOG.debug(line);
          }
        }
        else if (line.contains("java.lang.OutOfMemoryError")) {
          myContext.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, JpsBuildBundle.message("build.message.insufficient.memory")));
          myErrorCount.incrementAndGet();
        }
        else {
          myContext.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.INFO, line));
        }
      }
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      final CompilerMessage.Kind kind;
      switch (diagnostic.getKind()) {
        case ERROR:
          kind = BuildMessage.Kind.ERROR;
          myErrorCount.incrementAndGet();
          break;
        case MANDATORY_WARNING:
        case WARNING:
          kind = BuildMessage.Kind.WARNING;
          myWarningCount.incrementAndGet();
          break;
        case NOTE:
          kind = BuildMessage.Kind.INFO;
          break;
        case OTHER:
          kind = diagnostic instanceof JpsInfoDiagnostic? BuildMessage.Kind.JPS_INFO : BuildMessage.Kind.OTHER;
          break;
        default:
          kind = BuildMessage.Kind.OTHER;
      }
      File sourceFile = null;
      try {
        // for eclipse compiler just an attempt to call getSource() may lead to an NPE,
        // so calling this method under try/catch to avoid induced compiler errors
        final JavaFileObject source = diagnostic.getSource();
        sourceFile = source != null ? new File(source.toUri()) : null;
      }
      catch (Exception e) {
        LOG.info(e);
      }
      final String srcPath;
      if (sourceFile != null) {
        if (kind == BuildMessage.Kind.ERROR) {
          myFilesWithErrors.add(sourceFile);
        }
        srcPath = FileUtil.toSystemIndependentName(sourceFile.getPath());
      }
      else {
        srcPath = null;
      }
      String message = diagnostic.getMessage(Locale.US);
      if (Utils.IS_TEST_MODE) {
        LOG.info(message);
      }
      final CompilerMessage compilerMsg = new CompilerMessage(
        getBuilderName(), kind, message, srcPath, diagnostic.getStartPosition(),
        diagnostic.getEndPosition(), diagnostic.getPosition(), diagnostic.getLineNumber(),
        diagnostic.getColumnNumber()
      );
      if (LOG.isDebugEnabled()) {
        LOG.debug(compilerMsg.toString());
      }
      myContext.processMessage(compilerMsg);
    }

    int getErrorCount() {
      return myErrorCount.get();
    }

    int getWarningCount() {
      return myWarningCount.get();
    }

    @NotNull
    Collection<File> getFilesWithErrors() {
      return myFilesWithErrors;
    }
  }

  private static final class ExplodedModuleNameFinder implements Function<File, String> {
    private final TargetOutputIndex myOutsIndex;

    ExplodedModuleNameFinder(CompileContext context) {
      final BuildTargetIndex targetIndex = context.getProjectDescriptor().getBuildTargetIndex();
      final List<ModuleBuildTarget> javaModuleTargets = new ArrayList<>();
      for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
        javaModuleTargets.addAll(targetIndex.getAllTargets(type));
      }
      myOutsIndex = new TargetOutputIndexImpl(javaModuleTargets, context);
    }

    @Override
    public String apply(File outputDir) {
      for (BuildTarget<?> target : myOutsIndex.getTargetsByOutputFile(outputDir)) {
        if (target instanceof ModuleBasedTarget) {
          return ((ModuleBasedTarget<?>)target).getModule().getName().trim();
        }
      }
      return ModulePathSplitter.DEFAULT_MODULE_NAME_SEARCH.apply(outputDir);
    }
  }

  private final class ClassProcessingConsumer implements OutputFileConsumer {
    private final CompileContext myContext;
    private final OutputFileConsumer myDelegateOutputFileSink;

    private ClassProcessingConsumer(CompileContext context, OutputFileConsumer sink) {
      myContext = context;
      myDelegateOutputFileSink = sink != null ? sink : fileObject -> {
        throw new RuntimeException("Output sink for compiler was not specified");
      };
    }

    @Override
    public void save(final @NotNull OutputFileObject fileObject) {
      // generated files must be saved synchronously, because some compilers (e.g. eclipse)
      // may want to read them for further compilation
      try {
        final BinaryContent content = fileObject.getContent();
        final File file = fileObject.getFile();
        if (content != null) {
          content.saveToFile(file);
        }
        else {
          myContext.processMessage(new CompilerMessage(
            getBuilderName(), BuildMessage.Kind.WARNING, JpsBuildBundle.message("build.message.missing.content.for.file.0", file.getPath()))
          );
        }
      }
      catch (IOException e) {
        myContext.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, e.getMessage()));
      }

      submitAsyncTask(myContext, () -> {
        try {
          for (ClassPostProcessor processor : ourClassProcessors) {
            processor.process(myContext, fileObject);
          }
        }
        finally {
          myDelegateOutputFileSink.save(fileObject);
        }
      });
    }
  }

  @Override
  public long getExpectedBuildTime() {
    return 100;
  }

  private static final Key<Semaphore> COUNTER_KEY = Key.create("_async_task_counter_");
}
