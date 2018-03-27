/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.PersistentEnumeratorBase;
import com.intellij.util.lang.JavaVersion;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.PathUtils;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
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
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author Eugene Zhuravlev
 * @since 21.09.2011
 */
public class JavaBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.JavaBuilder");
  private static final String JAVA_EXTENSION = "java";

  public static final String BUILDER_NAME = "java";
  public static final Key<Boolean> IS_ENABLED = Key.create("_java_compiler_enabled_");
  public static final FileFilter JAVA_SOURCES_FILTER = FileFilters.withExtension(JAVA_EXTENSION);

  private static final Key<Boolean> PREFER_TARGET_JDK_COMPILER = GlobalContextKey.create("_prefer_target_jdk_javac_");
  private static final Key<JavaCompilingTool> COMPILING_TOOL = Key.create("_java_compiling_tool_");
  private static final Key<ConcurrentMap<String, Collection<String>>> COMPILER_USAGE_STATISTICS = Key.create("_java_compiler_usage_stats_");
  private static final List<String> COMPILABLE_EXTENSIONS = Collections.singletonList(JAVA_EXTENSION);
  private static final String MODULE_DIR_MACRO_TEMPLATE = "$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$";

  private static final Set<String> FILTERED_OPTIONS = ContainerUtil.newHashSet(
    "-target"
  );
  private static final Set<String> FILTERED_SINGLE_OPTIONS = ContainerUtil.newHashSet(
    "-g", "-deprecation", "-nowarn", "-verbose", "-proc:none", "-proc:only", "-proceedOnError"
  );

  private static final List<ClassPostProcessor> ourClassProcessors = new ArrayList<>();
  private static final Set<JpsModuleType<?>> ourCompilableModuleTypes = new HashSet<>();
  @Nullable private static final File ourDefaultRtJar;
  static {
    for (JavaBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(JavaBuilderExtension.class)) {
      ourCompilableModuleTypes.addAll(extension.getCompilableModuleTypes());
    }

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


  public static void registerClassPostProcessor(ClassPostProcessor processor) {
    ourClassProcessors.add(processor);
  }

  private final Executor myTaskRunner;

  public JavaBuilder(Executor tasksExecutor) {
    super(BuilderCategory.TRANSLATOR);
    myTaskRunner = new SequentialTaskExecutor("JavaBuilder pool", tasksExecutor);
    //add here class processors in the sequence they should be executed
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @Override
  public void buildStarted(CompileContext context) {
    final String compilerId = getUsedCompilerId(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Java compiler ID: " + compilerId);
    }
    JavaCompilingTool compilingTool = JavaBuilderUtil.findCompilingTool(compilerId);
    COMPILING_TOOL.set(context, compilingTool);
    COMPILER_USAGE_STATISTICS.set(context, new ConcurrentHashMap<>());
  }

  @Override
  public void chunkBuildStarted(final CompileContext context, final ModuleChunk chunk) {
    // before the first compilation round starts: find and mark dirty all classes that depend on removed or moved classes so
    // that all such files are compiled in the first round.
    try {
      JavaBuilderUtil.markDirtyDependenciesForInitialRound(context, new DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
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
    final ConcurrentMap<String, Collection<String>> stats = COMPILER_USAGE_STATISTICS.get(context);
    if (stats.size() == 1) {
      final Map.Entry<String, Collection<String>> entry = stats.entrySet().iterator().next();
      final String compilerName = entry.getKey();
      context.processMessage(new CompilerMessage("", BuildMessage.Kind.JPS_INFO, compilerName + " was used to compile java sources"));
      LOG.info(compilerName + " was used to compile " + entry.getValue());
    }
    else {
      for (Map.Entry<String, Collection<String>> entry : stats.entrySet()) {
        final String compilerName = entry.getKey();
        final Collection<String> moduleNames = entry.getValue();
        context.processMessage(new CompilerMessage("", BuildMessage.Kind.JPS_INFO,
          moduleNames.size() == 1 ?
          compilerName + " was used to compile [" + moduleNames.iterator().next() + "]" :
          compilerName + " was used to compile " + moduleNames.size() + " modules"
        ));
        LOG.info(compilerName + " was used to compile " + moduleNames);
      }
    }
  }

  @Override
  public List<String> getCompilableFileExtensions() {
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
      final Set<File> filesToCompile = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);

      dirtyFilesHolder.processDirtyFiles((target, file, descriptor) -> {
        if (JAVA_SOURCES_FILTER.accept(file) && ourCompilableModuleTypes.contains(target.getModule().getModuleType())) {
          filesToCompile.add(file);
        }
        return true;
      });


      int javaModulesCount = 0;
      if ((!filesToCompile.isEmpty() || dirtyFilesHolder.hasRemovedFiles()) && getTargetPlatformLanguageVersion(chunk.representativeTarget().getModule()) >= 9) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          if (JavaBuilderUtil.findModuleInfoFile(context, target) != null) {
            javaModulesCount++;
          }
        }
      }

      if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
        ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        if (logger.isEnabled() && !filesToCompile.isEmpty()) {
          logger.logCompiledFiles(filesToCompile, BUILDER_NAME, "Compiling files:");
        }
      }

      if (javaModulesCount > 1) {
        String prefix = "Cannot compile a module cycle with multiple module-info.java files: ";
        String message = chunk.getModules().stream().map(JpsModule::getName).collect(Collectors.joining(", ", prefix, ""));
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
        return ExitCode.ABORT;
      }

      return compile(context, chunk, dirtyFilesHolder, filesToCompile, outputConsumer, compilingTool, javaModulesCount > 0);
    }
    catch (BuildDataCorruptedException | PersistentEnumeratorBase.CorruptedException | ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      String message = e.getMessage();
      if (message == null) message = "Internal error: \n" + ExceptionUtil.getThrowableText(e);
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      throw new StopBuildException();
    }
  }

  private ExitCode compile(CompileContext context,
                           ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                           Collection<File> files,
                           OutputConsumer outputConsumer,
                           JavaCompilingTool compilingTool,
                           boolean hasModules) throws Exception {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final boolean hasSourcesToCompile = !files.isEmpty();

    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return exitCode;
    }

    final ProjectDescriptor pd = context.getProjectDescriptor();

    JavaBuilderUtil.ensureModuleHasJdk(chunk.representativeTarget().getModule(), context, BUILDER_NAME);
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
            compiledOk = compileJava(context, chunk, files, classpath, platformCp, srcPath, diagnosticSink, outputSink, compilingTool, hasModules);
          }
          finally {
            // heuristic: incorrect paths data recovery, so that the next make should not contain non-existing sources in 'recompile' list
            filesWithErrors = diagnosticSink.getFilesWithErrors();
            for (File file : filesWithErrors) {
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
            diagnosticSink.report(new JpsInfoDiagnostic("Errors occurred while compiling module '" + chunkName + "'"));
          }
          throw new StopBuildException(
            "Compilation failed: errors: " + diagnosticSink.getErrorCount() + "; warnings: " + diagnosticSink.getWarningCount()
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
                              Collection<File> files,
                              Collection<File> originalClassPath,
                              Collection<File> originalPlatformCp,
                              Collection<File> sourcePath,
                              DiagnosticOutputConsumer diagnosticSink,
                              OutputFileConsumer outputSink,
                              JavaCompilingTool compilingTool,
                              boolean hasModules) throws Exception {
    final Semaphore counter = new Semaphore();
    COUNTER_KEY.set(context, counter);

    final Set<JpsModule> modules = chunk.getModules();
    ProcessorConfigProfile profile = null;

    if (modules.size() == 1) {
      final JpsJavaCompilerConfiguration compilerConfig =
        JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
      assert compilerConfig != null;
      profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next());
    }
    else {
      final String message = validateCycle(context, chunk);
      if (message != null) {
        diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
        return true;
      }
    }

    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    try {
      final int targetLanguageLevel = getTargetPlatformLanguageVersion(chunk.representativeTarget().getModule());
      final boolean shouldForkJavac = shouldForkCompilerProcess(context, chunk, targetLanguageLevel);

      // when forking external javac, compilers from SDK 1.6 and higher are supported
      Pair<String, Integer> forkSdk = null;
      if (shouldForkJavac) {
        forkSdk = getForkedJavacSdk(chunk, targetLanguageLevel);
        if (forkSdk == null) {
          String text = "Cannot start javac process for " + chunk.getName() + ": unknown JDK home path.\nPlease check project configuration.";
          diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, text));
          return true;
        }
      }

      final int compilerSdkVersion = forkSdk == null ? JavaVersion.current().feature : forkSdk.getSecond();

      final Pair<List<String>, List<String>> vm_compilerOptions = getCompilationOptions(
        compilerSdkVersion, context, chunk, profile, compilingTool
      );
      final List<String> vmOptions = vm_compilerOptions.first;
      final List<String> options = vm_compilerOptions.second;

      if (LOG.isDebugEnabled()) {
        String mode = shouldForkJavac ? "fork" : "in-process";
        LOG.debug("Compiling chunk [" + chunk.getName() + "] with options: \"" + StringUtil.join(options, " ") + "\", mode=" + mode);
      }

      Collection<File> platformCp = calcEffectivePlatformCp(originalPlatformCp, options, compilingTool);
      if (platformCp == null) {
        String text = "Compact compilation profile was requested, but target platform for module \"" + chunk.getName() + "\"" +
                      " differs from javac's platform (" + System.getProperty("java.version") + ")\n" +
                      "Compilation profiles are not supported for such configuration";
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, text));
        return true;
      }

      Collection<File> classPath = originalClassPath;
      Collection<File> modulePath = Collections.emptyList();

      if (hasModules) {
        // in Java 9, named modules are not allowed to read classes from the classpath
        // moreover, the compiler requires all transitive dependencies to be on the module path
        modulePath = ProjectPaths.getCompilationModulePath(chunk, false);
        classPath = Collections.emptyList();
      }

      if (!platformCp.isEmpty()) {
        if (hasModules) {
          modulePath = JBIterable.from(platformCp).append(modulePath).toList();
          platformCp = Collections.emptyList();
        }
        else if ((getChunkSdkVersion(chunk)) >= 9) {
          // if chunk's SDK is 9 or higher, there is no way to specify full platform classpath
          // because platform classes are stored in jimage binary files with unknown format.
          // Because of this we are clearing platform classpath so that javac will resolve against its own boot classpath
          // and prepending additional jars from the JDK configuration to compilation classpath
          classPath = JBIterable.from(platformCp).append(classPath).toList();
          platformCp = Collections.emptyList();
        }
      }

      final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
      final boolean rc;
      if (!shouldForkJavac) {
        updateCompilerUsageStatistics(context, compilingTool.getDescription(), chunk);
        rc = JavacMain.compile(
          options, files, classPath, platformCp, modulePath, sourcePath, outs, diagnosticSink, classesConsumer,
          context.getCancelStatus(), compilingTool
        );
      }
      else {
        updateCompilerUsageStatistics(context, "javac " + forkSdk.getSecond(), chunk);
        final ExternalJavacManager server = ensureJavacServerStarted(context);
        rc = server.forkJavac(
          forkSdk.getFirst(),
          getExternalJavacHeapSize(),
          vmOptions, options, platformCp, classPath, modulePath, sourcePath,
          files, outs, diagnosticSink, classesConsumer, compilingTool, context.getCancelStatus()
        );
      }
      return rc;
    }
    finally {
      counter.waitFor();
    }
  }

  private static void updateCompilerUsageStatistics(CompileContext context, String compilerName, ModuleChunk chunk) {
    final ConcurrentMap<String, Collection<String>> map = COMPILER_USAGE_STATISTICS.get(context);
    Collection<String> names = map.get(compilerName);
    if (names == null) {
      names = Collections.synchronizedSet(new HashSet<String>());
      final Collection<String> prev = map.putIfAbsent(compilerName, names);
      if (prev != null) {
        names = prev;
      }
    }
    for (JpsModule module : chunk.getModules()) {
      names.add(module.getName());
    }
  }

  private static int getExternalJavacHeapSize() {
    //final JpsProject project = context.getProjectDescriptor().getProject();
    //final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    //final JpsJavaCompilerOptions options = config.getCurrentCompilerOptions();
    //return options.MAXIMUM_HEAP_SIZE;
    final int maxMbytes = (int)(Runtime.getRuntime().maxMemory() / 1048576L);
    if (maxMbytes < 0) {
      return -1; // in case of int overflow, return -1 to let VM choose the heap size
    }
    return Math.max(maxMbytes * 75 / 100, 256); // minimum 256 Mb, maximum 75% from JPS max heap size
  }

  @Nullable
  public static String validateCycle(CompileContext context, ModuleChunk chunk) {
    final JpsJavaExtensionService javaExt = JpsJavaExtensionService.getInstance();
    final JpsJavaCompilerConfiguration compilerConfig = javaExt.getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert compilerConfig != null;
    final Set<JpsModule> modules = chunk.getModules();
    Pair<String, LanguageLevel> pair = null;
    for (JpsModule module : modules) {
      final LanguageLevel moduleLevel = javaExt.getLanguageLevel(module);
      if (pair == null) {
        pair = Pair.create(module.getName(), moduleLevel); // first value
      }
      else if (!Comparing.equal(pair.getSecond(), moduleLevel)) {
        return "Modules " + pair.getFirst() + " and " + module.getName() +
               " must have the same language level because of cyclic dependencies between them";
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
              return "Modules " + overridden.first + " and " + module.getName() + " must have the same 'additional command line parameters' specified because of cyclic dependencies between them";
            }
          }
        }
        else {
          context.processMessage(new CompilerMessage(
            BUILDER_NAME, BuildMessage.Kind.WARNING,
            "Some modules with cyclic dependencies [" + chunk.getName() + "] have 'additional command line parameters' overridden in project settings.\nThese compilation options were applied to all modules in the cycle."
          ));
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

  private static Set<String> parseOptions(String str) {
    final Set<String> result = new SmartHashSet<>();
    StringTokenizer t = new StringTokenizer(str, " \n\t", false);
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }
    return result;
  }

  private static boolean shouldUseReleaseOption(CompileContext context, int compilerVersion, int chunkSdkVersion, int targetPlatformVersion) {
    // -release option makes sense for javac only and is supported in java9+ and higher
    if (compilerVersion >= 9 && chunkSdkVersion > 0 && targetPlatformVersion > 0 && isJavac(COMPILING_TOOL.get(context))) {
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

  private static boolean shouldForkCompilerProcess(CompileContext context, ModuleChunk chunk, int chunkLanguageLevel) {
    if (!isJavac(COMPILING_TOOL.get(context))) {
      return false; // applicable to javac only
    }
    final int compilerSdkVersion = JavaVersion.current().feature;

    if (preferTargetJdkCompiler(context)) {
      final Pair<JpsSdk<JpsDummyElement>, Integer> sdkVersionPair = getAssociatedSdk(chunk);
      if (sdkVersionPair != null) {
        final Integer chunkSdkVersion = sdkVersionPair.second;
        if (chunkSdkVersion != compilerSdkVersion && chunkSdkVersion >= 6 /*min. supported compiler version*/) {
          // there is a special case because of difference in type inference behavior between javac8 and javac6-javac7
          // so if corresponding JDK is associated with the module chunk, prefer compiler from this JDK over the newer compiler version
          return true;
        }
      }
    }

    if (compilerSdkVersion < 9 || chunkLanguageLevel <= 0) {
      // javac up to version 9 supports all previous releases
      // or
      // was not able to determine jdk version, so assuming in-process compiler
      return false;
    }
    // compilerSdkVersion is 9+ here, so applying JEP 182 "Retiring javac 'one plus three back'" policy
    return Math.abs(compilerSdkVersion - chunkLanguageLevel) > 3;
  }

  private static boolean isJavac(final JavaCompilingTool compilingTool) {
    return compilingTool != null && (compilingTool.getId() == JavaCompilers.JAVAC_ID || compilingTool.getId() == JavaCompilers.JAVAC_API_ID);
  }

  private static boolean preferTargetJdkCompiler(CompileContext context) {
    Boolean val = PREFER_TARGET_JDK_COMPILER.get(context);
    if (val == null) {
      final JpsProject project = context.getProjectDescriptor().getProject();
      final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
      // default
      val = config != null? config.getCompilerOptions(JavaCompilers.JAVAC_ID).PREFER_TARGET_JDK_COMPILER : Boolean.TRUE;
      PREFER_TARGET_JDK_COMPILER.set(context, val);
    }
    return val;
  }

  // If platformCp of the build process is the same as the target platform, do not specify platformCp explicitly
  // this will allow javac to resolve against ct.sym file, which is required for the "compilation profiles" feature
  @Nullable
  private static Collection<File> calcEffectivePlatformCp(Collection<File> platformCp, List<String> options, JavaCompilingTool compilingTool) {
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
        context.processMessage(new CompilerMessage(BUILDER_NAME, e));
      }
      finally {
        counter.up();
      }
    });
  }

  @NotNull
  private static synchronized ExternalJavacManager ensureJavacServerStarted(@NotNull CompileContext context) {
    ExternalJavacManager server = ExternalJavacManager.KEY.get(context);
    if (server != null) {
      return server;
    }
    final int listenPort = findFreePort();
    server = new ExternalJavacManager(Utils.getSystemRoot()) {
      @Override
      protected ExternalJavacProcessHandler createProcessHandler(@NotNull Process process, @NotNull String commandLine) {
        return new ExternalJavacProcessHandler(process, commandLine) {
          @Override
          @NotNull
          protected Future<?> executeOnPooledThread(@NotNull Runnable task) {
            return SharedThreadPool.getInstance().executeOnPooledThread(task);
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

  private static Pair<List<String>, List<String>> getCompilationOptions(int compilerSdkVersion,
                                                                        CompileContext context,
                                                                        ModuleChunk chunk,
                                                                        @Nullable ProcessorConfigProfile profile,
                                                                        @NotNull JavaCompilingTool compilingTool) {
    final List<String> _compilationOptions = new ArrayList<>();
    final List<String> vmOptions = new ArrayList<>();
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerOptions compilerOptions = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getCurrentCompilerOptions();
    if (compilerOptions.DEBUGGING_INFO) {
      _compilationOptions.add("-g");
    }
    if (compilerOptions.DEPRECATION) {
      _compilationOptions.add("-deprecation");
    }
    if (compilerOptions.GENERATE_NO_WARNINGS) {
      _compilationOptions.add("-nowarn");
    }
    if (compilerOptions instanceof EclipseCompilerOptions) {
      final EclipseCompilerOptions eclipseOptions = (EclipseCompilerOptions)compilerOptions;
      if (eclipseOptions.PROCEED_ON_ERROR) {
        Utils.PROCEED_ON_ERROR_KEY.set(context, Boolean.TRUE);
        _compilationOptions.add("-proceedOnError");
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

    if (customArgs != null) {
      BiConsumer<List<String>, String> appender = List::add;
      final JpsModule module = chunk.representativeTarget().getModule();
      final File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
      if (baseDirectory != null) {
        //this is a temporary workaround to allow passing per-module compiler options for Eclipse compiler in form
        // -properties $MODULE_DIR$/.settings/org.eclipse.jdt.core.prefs
        final String moduleDirPath = FileUtil.toCanonicalPath(baseDirectory.getAbsolutePath());
        appender = (strings, option) -> strings.add(StringUtil.replace(option, MODULE_DIR_MACRO_TEMPLATE, moduleDirPath));
      }

      boolean skip = false;
      boolean targetOptionFound = false;
      for (final String userOption : ParametersListUtil.parse(customArgs)) {
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
              appender.accept(_compilationOptions, userOption);
            }
          }
        }
      }
    }

    for (ExternalJavacOptionsProvider extension : JpsServiceManager.getInstance().getExtensions(ExternalJavacOptionsProvider.class)) {
      vmOptions.addAll(extension.getOptions(compilingTool));
    }
    addCompilationOptions(compilerSdkVersion, _compilationOptions, context, chunk, profile);

    return Pair.create(vmOptions, _compilationOptions);
  }

  public static void addCompilationOptions(List<String> options,
                                           CompileContext context,
                                           ModuleChunk chunk,
                                           @Nullable ProcessorConfigProfile profile) {
    addCompilationOptions(JavaVersion.current().feature, options, context, chunk, profile);
  }

  private static void addCompilationOptions(int compilerSdkVersion,
                                            List<String> options,
                                            CompileContext context, ModuleChunk chunk,
                                            @Nullable ProcessorConfigProfile profile) {
    if (!options.contains("-encoding")) {
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

    addCrossCompilationOptions(compilerSdkVersion, options, context, chunk);

    if (addAnnotationProcessingOptions(options, profile)) {
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
  public static boolean addAnnotationProcessingOptions(List<String> options, @Nullable AnnotationProcessingConfiguration profile) {
    if (profile == null || !profile.isEnabled()) {
      options.add("-proc:none");
      return false;
    }

    // configuring annotation processing
    if (!profile.isObtainProcessorsFromClasspath()) {
      final String processorsPath = profile.getProcessorPath();
      options.add("-processorpath");
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

  @NotNull
  public static String getUsedCompilerId(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    return config == null ? JavaCompilers.JAVAC_ID : config.getJavaCompilerId();
  }

  private static void addCrossCompilationOptions(int compilerSdkVersion, List<String> options, CompileContext context, ModuleChunk chunk) {
    final JpsJavaCompilerConfiguration compilerConfiguration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      context.getProjectDescriptor().getProject()
    );

    final String langLevel = getLanguageLevel(chunk.getModules().iterator().next());
    final int chunkSdkVersion = getChunkSdkVersion(chunk);

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

    final int targetPlatformVersion = JpsJavaSdkType.parseVersion(bytecodeTarget);
    if (shouldUseReleaseOption(context, compilerSdkVersion, chunkSdkVersion, targetPlatformVersion)) {
      options.add("--release");
      options.add(String.valueOf(targetPlatformVersion));
      return;
    }

    // using older -source, -target and -bootclasspath options
    if (!StringUtil.isEmpty(langLevel)) {
      options.add("-source");
      options.add(langLevel);
    }

    if (bytecodeTarget != null) {
      options.add("-target");
      if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
        // if compiler is newer than module JDK
        if (targetPlatformVersion > 0 && targetPlatformVersion <= compilerSdkVersion) {
          // if user-specified bytecode version can be determined and is supported by compiler
          if (targetPlatformVersion > chunkSdkVersion) {
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
  }

  @Nullable
  private static String getLanguageLevel(@NotNull JpsModule module) {
    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    return level != null ? level.getComplianceOption() : null;
  }

  /**
   * The assumed module's source code language version.
   * Returns the version number, corresponding to the language level, associated with the given module.
   * If no language level set (neither on module- nor on project-level), the version of JDK associated with the module is returned.
   * If no JDK is associated, returns 0.
   */
  private static int getTargetPlatformLanguageVersion(@NotNull JpsModule module) {
    final String level = getLanguageLevel(module);
    if (level != null) {
      return JpsJavaSdkType.parseVersion(level);
    }
    // when compiling, if language level is not explicitly set, it is assumed to be equal to
    // the highest possible language level, that target JDK supports
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

  @Nullable
  private static Pair<String, Integer> getForkedJavacSdk(ModuleChunk chunk, int targetLanguageLevel) {
    final Pair<JpsSdk<JpsDummyElement>, Integer> sdkVersionPair = getAssociatedSdk(chunk);
    if (sdkVersionPair != null) {
      final int sdkVersion = sdkVersionPair.second;
      if (sdkVersion >= 6 && (sdkVersion < 9 || Math.abs(sdkVersion - targetLanguageLevel) <= 3)) {
        // current javac compiler does support required language level
        return Pair.create(sdkVersionPair.first.getHomePath(), sdkVersion);
      }
    }
    final String fallbackJdkHome = System.getProperty(GlobalOptions.FALLBACK_JDK_HOME, null);
    if (fallbackJdkHome == null) {
      LOG.info("Fallback JDK is not specified. (See " + GlobalOptions.FALLBACK_JDK_HOME + " option)");
      return null;
    }
    final String fallbackJdkVersion = System.getProperty(GlobalOptions.FALLBACK_JDK_VERSION, null);
    if (fallbackJdkVersion == null) {
      LOG.info("Fallback JDK version is not specified. (See " + GlobalOptions.FALLBACK_JDK_VERSION + " option)");
      return null;
    }
    final int fallbackVersion = JpsJavaSdkType.parseVersion(fallbackJdkVersion);
    if (fallbackVersion < 6) {
      LOG.info("Version string for fallback JDK is '" + fallbackJdkVersion + "' (recognized as version '" + fallbackJdkVersion + "')." +
               " At least version 6 is required.");
      return null;
    }
    return Pair.create(fallbackJdkHome, fallbackVersion);
  }

  @Nullable
  private static Pair<JpsSdk<JpsDummyElement>, Integer> getAssociatedSdk(ModuleChunk chunk) {
    // assuming all modules in the chunk have the same associated JDK;
    // this constraint should be validated on build start
    final JpsSdk<JpsDummyElement> sdk = chunk.representativeTarget().getModule().getSdk(JpsJavaSdkType.INSTANCE);
    return sdk != null ? Pair.create(sdk, JpsJavaSdkType.getJavaVersion(sdk)) : null;
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      final File outputDir = target.getOutputDir();
      if (outputDir == null) {
        continue;
      }
      final Set<File> roots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      for (JavaSourceRootDescriptor descriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
        roots.add(descriptor.root);
      }
      map.put(outputDir, roots);
    }
    return map;
  }

  private static class DiagnosticSink implements DiagnosticOutputConsumer {
    private final CompileContext myContext;
    private volatile int myErrorCount;
    private volatile int myWarningCount;
    private final Set<File> myFilesWithErrors = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);

    private DiagnosticSink(CompileContext context) {
      myContext = context;
    }

    @Override
    public void javaFileLoaded(File file) {
    }

    @Override
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

    @Override
    public void customOutputData(String pluginId, String dataName, byte[] data) {
      for (CustomOutputDataListener listener : JpsServiceManager.getInstance().getExtensions(CustomOutputDataListener.class)) {
        if (pluginId.equals(listener.getId())) {
          listener.processData(dataName, data);
          return;
        }
      }
    }

    @Override
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

    @Override
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
        sourceFile = source != null ? PathUtils.convertToFile(source.toUri()) : null;
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
        BUILDER_NAME, kind, message, srcPath, diagnostic.getStartPosition(),
        diagnostic.getEndPosition(), diagnostic.getPosition(), diagnostic.getLineNumber(),
        diagnostic.getColumnNumber()
      );
      if (LOG.isDebugEnabled()) {
        LOG.debug(compilerMsg.toString());
      }
      myContext.processMessage(compilerMsg);
    }

    int getErrorCount() {
      return myErrorCount;
    }

    int getWarningCount() {
      return myWarningCount;
    }

    @NotNull
    Collection<File> getFilesWithErrors() {
      return myFilesWithErrors;
    }
  }

  private class ClassProcessingConsumer implements OutputFileConsumer {
    private final CompileContext myContext;
    private final OutputFileConsumer myDelegateOutputFileSink;

    private ClassProcessingConsumer(CompileContext context, OutputFileConsumer sink) {
      myContext = context;
      myDelegateOutputFileSink = sink != null ? sink : fileObject -> {
        throw new RuntimeException("Output sink for compiler was not specified");
      };
    }

    @Override
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


  private static final Key<Semaphore> COUNTER_KEY = Key.create("_async_task_counter_");
}