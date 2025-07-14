// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Formats;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.tracing.Tracer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.BuildParametersKeys;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.fs.FilesDelta;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.*;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.javac.ExternalJavacManager;
import org.jetbrains.jps.javac.ExternalJavacManagerKey;
import org.jetbrains.jps.javac.JavacMain;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.TimingLog;
import org.jetbrains.jps.service.SharedThreadPool;
import org.jetbrains.jps.util.JpsPathUtil;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jetbrains.jps.builders.java.JavaBuilderUtil.isDepGraphEnabled;
import static org.jetbrains.jps.builders.java.JavaBuilderUtil.isTrackLibraryDependenciesEnabled;

@SuppressWarnings("BoundedWildcard")
@ApiStatus.Internal
public final class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance(IncProjectBuilder.class);
  private static final MethodHandles.Lookup ourLookup = MethodHandles.lookup();

  private static final String CLASSPATH_INDEX_FILE_NAME = "classpath.index";
  // CLASSPATH_INDEX_FILE_NAME cannot be used because IDEA on run creates CLASSPATH_INDEX_FILE_NAME only if some module class is loaded,
  // so, not possible to distinguish case
  // "classpath.index doesn't exist because deleted on module file change" vs. "classpath.index doesn't exist because was not created"
  private static final String UNMODIFIED_MARK_FILE_NAME = ".unmodified";

  private static final int FLUSH_INVOCATIONS_TO_SKIP = 10;

  private static final boolean SYNC_DELETE = Boolean.parseBoolean(System.getProperty("jps.sync.delete", "false"));
  private static final GlobalContextKey<Set<BuildTarget<?>>> TARGET_WITH_CLEARED_OUTPUT = GlobalContextKey.create("_targets_with_cleared_output_");
  public static final int MAX_BUILDER_THREADS;
  static {
    int maxThreads = Math.min(10, (75 * Runtime.getRuntime().availableProcessors()) / 100); // 75% of available logical cores, but not more than 10 threads
    try {
      maxThreads = Math.max(1, Integer.parseInt(System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Integer.toString(maxThreads))));
    }
    catch (NumberFormatException ignored) {
      maxThreads = Math.max(1, maxThreads);
    }
    MAX_BUILDER_THREADS = maxThreads;
  }

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  private final List<MessageHandler> messageHandlers = new ArrayList<>();
  private final MessageHandler myMessageDispatcher = message -> {
    for (MessageHandler h : messageHandlers) {
      h.processMessage(message);
    }
  };
  private final boolean myIsTestMode;

  private final int myTotalModuleLevelBuilderCount;
  private final List<Future<?>> myAsyncTasks = Collections.synchronizedList(new ArrayList<>());
  private final ConcurrentMap<Builder, AtomicLong> myElapsedTimeNanosByBuilder = new ConcurrentHashMap<>();
  private final ConcurrentMap<Builder, AtomicInteger> myNumberOfSourcesProcessedByBuilder = new ConcurrentHashMap<>();

  public IncProjectBuilder(@NotNull ProjectDescriptor projectDescriptor,
                           @NotNull BuilderRegistry builderRegistry,
                           @NotNull Map<String, String> builderParams,
                           @NotNull CanceledStatus canceledStatus,
                           boolean isTestMode) {
    myProjectDescriptor = projectDescriptor;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = canceledStatus;
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
    myIsTestMode = isTestMode;
  }

  public void addMessageHandler(MessageHandler handler) {
    messageHandlers.add(handler);
  }

  public void checkUpToDate(@NotNull CompileScope scope) {
    CompileContextImpl context = createContext(scope);
    try {
      final BuildFSState fsState = myProjectDescriptor.fsState;

      ExecutorService executor = SharedThreadPool.getInstance().createBoundedExecutor("IncProjectBuilder Check UpToDate Pool", MAX_BUILDER_THREADS);
      List<Future<?>> tasks = new ArrayList<>();

      var notifier = new Object() {
        private final AtomicBoolean hasWorkToDo = new AtomicBoolean(false);

        boolean hasChanges() {
          return hasWorkToDo.get();
        }

        void signalHasChanges() {
          if (!hasWorkToDo.getAndSet(true)) {
            // this will serve as a marker that compiler has work to do
            myMessageDispatcher.processMessage(DoneSomethingNotification.INSTANCE);
          }
        }
      };

      for (BuildTarget<?> target : myProjectDescriptor.getBuildTargetIndex().getAllTargets()) {
        if (notifier.hasChanges()) {
          break;
        }
        if (scope.isAffected(target)) {
          tasks.add(executor.submit(() -> {
            try {
              if (notifier.hasChanges()) {
                return;
              }
              if (context.getCancelStatus().isCanceled()) {
                notifier.signalHasChanges(); // unable to check all targets => assume has changes
                return;
              }
              BuildOperations.ensureFSStateInitialized(context, target, true);
              final FilesDelta delta = fsState.getEffectiveFilesDelta(context, target);
              delta.lockData();
              try {
                for (Set<Path> files : delta.getSourceSetsToRecompile()) {
                  for (Path file : files) {
                    if (scope.isAffected(target, file)) {
                      notifier.signalHasChanges();
                      return;
                    }
                  }
                }
              }
              finally {
                delta.unlockData();
              }
            }
            catch (Throwable e) {
              LOG.info(e);
              notifier.signalHasChanges(); // data can be corrupted => rebuild required => has changes
            }
          }));
        }
      }

      for (Future<?> task : tasks) {
        try {
          task.get();
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }
    }
    finally {
      flushContext(context);
    }
  }


  public void build(@NotNull CompileScope scope, boolean forceCleanCaches) throws RebuildRequestedException {
    Tracer.Span rebuildRequiredSpan = Tracer.start("IncProjectBuilder.checkRebuildRequired");
    checkRebuildRequired(scope);
    rebuildRequiredSpan.complete();

    Collection<Future<?>> currentTasks = CleanupTempDirectoryExtension.getRunningCleanupTasks();
    if (!currentTasks.isEmpty()) {
      myAsyncTasks.addAll(currentTasks);
    }
    else {
      Future<?> cleanupTask = CleanupTempDirectoryExtension.startTempDirectoryCleanupTask(myProjectDescriptor);
      if (cleanupTask != null) {
        myAsyncTasks.add(cleanupTask);
      }
    }

    BuildDataManager dataManager = myProjectDescriptor.dataManager;

    LowMemoryWatcher memWatcher = LowMemoryWatcher.register(() -> {
      JavacMain.clearCompilerZipFileCache();
      dataManager.flush(false);
      dataManager.clearCache();
    });

    CompileContextImpl context = null;
    BuildTargetSourcesState sourcesState = null;
    try {
      context = createContext(scope);
      sourcesState = new BuildTargetSourcesState(context);
      // clear source state report if force clean or rebuild
      if (forceCleanCaches || JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
        sourcesState.clearSourcesState();
      }

      Tracer.Span buildSpan = Tracer.start("IncProjectBuilder.runBuild");
      runBuild(context, forceCleanCaches);
      buildSpan.complete();
      dataManager.saveVersion();
      dataManager.reportUnhandledRelativizerPaths();
      sourcesState.reportSourcesState();
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
    }
    catch (StopBuildException e) {
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
      // if build was canceled for some reason, e.g., compilation error, we should report built modules
      sourcesState.reportSourcesState();
      // some builder decided to stop the build, report an optional progress message if any
      String message = e.getMessage();
      if (message != null && !message.isBlank()) {
        myMessageDispatcher.processMessage(new ProgressMessage(message));
      }
    }
    catch (BuildDataCorruptedException e) {
      LOG.info(e);
      requestRebuild(e, e);
    }
    catch (ProjectBuildException e) {
      LOG.info(e);
      Throwable cause = e.getCause();
      if (cause instanceof IOException ||
          cause instanceof BuildDataCorruptedException ||
          (cause instanceof RuntimeException && cause.getCause() instanceof IOException)) {
        requestRebuild(e, cause);
      }
      else {
        // should stop the build with error
        myMessageDispatcher.processMessage(getCompilerMessage(e, cause));
      }
    }
    finally {
      Tracer.Span finishingCompilationSpan = Tracer.start("finishing compilation");
      memWatcher.stop();
      flushContext(context);
      // wait for async tasks
      CanceledStatus status = context == null ? CanceledStatus.NULL : context.getCancelStatus();
      synchronized (myAsyncTasks) {
        for (Future<?> task : myAsyncTasks) {
          if (status.isCanceled()) {
            break;
          }
          waitForTask(status, task);
        }
      }
      finishingCompilationSpan.complete();
    }
  }

  private static @NotNull CompilerMessage getCompilerMessage(@NotNull ProjectBuildException e, @Nullable Throwable cause) {
    String errorMessage = e.getMessage();
    if (errorMessage == null || errorMessage.isBlank()) {
      return CompilerMessage.createInternalCompilationError("", cause == null ? e : cause);
    }

    String causeMessage = cause == null ? null : cause.getMessage();
    String text = causeMessage == null || causeMessage.isBlank() || errorMessage.trim().endsWith(causeMessage)
                  ? errorMessage
                  : errorMessage + ": " + causeMessage;
    return new CompilerMessage("", BuildMessage.Kind.ERROR, text);
  }

  private void checkRebuildRequired(@NotNull CompileScope scope) throws RebuildRequestedException {
    boolean isDebugEnabled = LOG.isDebugEnabled();
    if (myIsTestMode || isAutoBuild()) {
      // do not use the heuristic in tests in order to properly test all cases
      // automatic builds should not cause to start full project rebuilds to avoid situations when user does not expect rebuild
      if (isDebugEnabled) {
        LOG.debug("Rebuild heuristic: skipping the check; isTestMode = " + myIsTestMode + "; isAutoBuild = " + isAutoBuild());
      }
      return;
    }

    BuildTargetStateManager targetStateManager = myProjectDescriptor.dataManager.getTargetStateManager();
    long timeThreshold = targetStateManager.getLastSuccessfulRebuildDuration() * 95 / 100; // 95% of last registered clean rebuild time
    if (timeThreshold <= 0) {
      if (isDebugEnabled) {
        LOG.debug("Rebuild heuristic: no stats available");
      }
      return;
    }

    // check that this is a whole-project incremental build
    // checking only JavaModuleBuildTargetType because these target types directly correspond to project modules
    for (BuildTargetType<?> type : JavaModuleBuildTargetType.ALL_TYPES) {
      if (!scope.isBuildIncrementally(type)) {
        if (isDebugEnabled) {
          LOG.debug("Rebuild heuristic: skipping the check because rebuild is forced for targets of type " + type.getTypeId());
        }
        return;
      }
      if (!scope.isAllTargetsOfTypeAffected(type)) {
        if (isDebugEnabled) {
          LOG.debug("Rebuild heuristic: skipping the check because some targets are excluded from compilation scope, e.g. targets of type " + type.getTypeId());
        }
        return;
      }
    }

    // compute estimated times for dirty targets
    long estimatedWorkTime = calculateEstimatedBuildTime(myProjectDescriptor, new Predicate<>() {
      private final Set<BuildTargetType<?>> allTargetsAffected = new HashSet<>(JavaModuleBuildTargetType.ALL_TYPES);
      @Override
      public boolean test(BuildTarget<?> target) {
        // optimization, since we know here that all targets of types JavaModuleBuildTargetType are affected
        return allTargetsAffected.contains(target.getTargetType()) || scope.isAffected(target);
      }
    });
    if (isDebugEnabled) {
      LOG.debug("Rebuild heuristic: estimated build time / timeThreshold : " + estimatedWorkTime + " / " + timeThreshold);
    }

    if (estimatedWorkTime >= timeThreshold) {
      String message = JpsBuildBundle.message("build.message.too.many.modules.require.recompilation.forcing.full.project.rebuild");
      LOG.info(message);
      LOG.info("Estimated build duration (linear): " + Formats.formatDuration(estimatedWorkTime));
      LOG.info("Last successful rebuild duration (linear): " + Formats.formatDuration(targetStateManager.getLastSuccessfulRebuildDuration()));
      LOG.info("Rebuild heuristic time threshold: " + Formats.formatDuration(timeThreshold));
      myMessageDispatcher.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, message));
      throw new RebuildRequestedException(null);
    }
  }

  public static long calculateEstimatedBuildTime(@NotNull ProjectDescriptor projectDescriptor, @NotNull Predicate<BuildTarget<?>> isAffected) {
    BuildTargetStateManager targetStateManager = projectDescriptor.dataManager.getTargetStateManager();
    // compute estimated times for dirty targets
    long estimatedBuildTime = 0L;

    final BuildTargetIndex targetIndex = projectDescriptor.getBuildTargetIndex();
    int affectedTargets = 0;
    for (BuildTarget<?> target : targetIndex.getAllTargets()) {
      if (!targetIndex.isDummy(target)) {
        final long avgTimeToBuild = targetStateManager.getAverageBuildTime(target.getTargetType());
        if (avgTimeToBuild > 0) {
          // 1. in general case, this time should include dependency analysis and cache update times
          // 2. need to check isAffected() since some targets (like artifacts) may be unaffected even for rebuild
          if (targetStateManager.getTargetConfiguration(target).isTargetDirty(projectDescriptor) && isAffected.test(target)) {
            estimatedBuildTime += avgTimeToBuild;
            affectedTargets++;
          }
        }
      }
    }
    LOG.info("Affected build targets count: " + affectedTargets);
    return estimatedBuildTime;
  }

  private void requestRebuild(Exception e, Throwable cause) throws RebuildRequestedException {
    myMessageDispatcher.processMessage(new CompilerMessage(
      "", BuildMessage.Kind.INFO, JpsBuildBundle.message("build.message.internal.caches.are.corrupted", e.getMessage()))
    );
    throw new RebuildRequestedException(cause);
  }

  private static void waitForTask(@NotNull CanceledStatus status, Future<?> task) {
    try {
      while (true) {
        try {
          task.get(500L, TimeUnit.MILLISECONDS);
          break;
        }
        catch (TimeoutException ignored) {
          if (status.isCanceled()) {
            break;
          }
        }
      }
    }
    catch (Throwable th) {
      LOG.info(th);
    }
  }

  private static void reportRebuiltModules(CompileContextImpl context) {
    final Set<JpsModule> modules = BuildTargetConfiguration.MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.get(context);
    if (modules == null || modules.isEmpty()) {
      return;
    }
    int shown = modules.size() == 6 ? 6 : Math.min(5, modules.size());
    String modulesText = modules.stream().limit(shown).map(m -> "'" + m.getName() + "'").collect(Collectors.joining(", "));
    String text = JpsBuildBundle.message("build.messages.modules.were.fully.rebuilt", modulesText, modules.size(),
                                         modules.size() - shown, ModuleBuildTarget.REBUILD_ON_DEPENDENCY_CHANGE ? 1 : 0);
    context.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, text));
  }

  private static void reportUnprocessedChanges(CompileContextImpl context) {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final BuildFSState fsState = pd.fsState;
    for (BuildTarget<?> target : pd.getBuildTargetIndex().getAllTargets()) {
      if (fsState.hasUnprocessedChanges(context, target)) {
        context.processMessage(new UnprocessedFSChangesNotification());
        break;
      }
    }
  }

  private static void flushContext(CompileContext context) {
    if (context != null) {
      context.getProjectDescriptor().dataManager.flush(false);
    }

    ExternalJavacManager server = ExternalJavacManagerKey.KEY.get(context);
    if (server != null) {
      server.stop();
      ExternalJavacManagerKey.KEY.set(context, null);
    }
  }

  private boolean isAutoBuild() {
    return Boolean.parseBoolean(myBuilderParams.get(BuildParametersKeys.IS_AUTOMAKE));
  }

  private boolean isParallelBuild() {
    return isAutoBuild() ? BuildRunner.isParallelBuildAutomakeEnabled() : BuildRunner.isParallelBuildEnabled();
  }

  private void runBuild(@NotNull CompileContextImpl context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info(
      "Building project; isRebuild:" +
        JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) +
        "; isMake:" +
        context.isMake() +
        " parallel compilation:" +
        isParallelBuild() +
        "; dependency graph enabled:" +
        isDepGraphEnabled() +
        "; library dependencies tracking enabled:" +
        isTrackLibraryDependenciesEnabled()
    );

    context.addBuildListener(new ChainedTargetsBuildListener(context));

    // deletes class loader classpath index files for changed output roots
    context.addBuildListener(new BuildListener() {
      @Override
      public void filesGenerated(@NotNull FileGeneratedEvent event) {
        Collection<Pair<String, String>> paths = event.getPaths();
        FileSystem fs = FileSystems.getDefault();
        if (paths.size() == 1) {
          deleteFiles(paths.iterator().next().first, fs);
          return;
        }

        Set<String> outputs = new HashSet<>();
        for (Pair<String, String> pair : paths) {
          String root = pair.getFirst();
          if (outputs.add(root)) {
            deleteFiles(root, fs);
          }
        }
      }

      private void deleteFiles(String rootPath, FileSystem fs) {
        Path root = fs.getPath(rootPath);
        try {
          Files.deleteIfExists(root.resolve(CLASSPATH_INDEX_FILE_NAME));
          Files.deleteIfExists(root.resolve(UNMODIFIED_MARK_FILE_NAME));
        }
        catch (IOException ignore) {
        }
      }
    });
    Tracer.Span allTargetBuilderBuildStartedSpan = Tracer.start("All TargetBuilder.buildStarted");
    for (TargetBuilder<?, ?> builder : myBuilderRegistry.getTargetBuilders()) {
      builder.buildStarted(context);
    }
    allTargetBuilderBuildStartedSpan.complete();
    Tracer.Span allModuleLevelBuildersBuildStartedSpan = Tracer.start("All ModuleLevelBuilder.buildStarted");
    for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
      builder.buildStarted(context);
    }
    allModuleLevelBuildersBuildStartedSpan.complete();

    BuildProgress buildProgress = null;
    try {
      buildProgress = new BuildProgress(myProjectDescriptor.dataManager, myProjectDescriptor.getBuildTargetIndex(),
                                        myProjectDescriptor.getBuildTargetIndex().getSortedTargetChunks(context),
                                        chunk -> isAffected(context.getScope(), chunk));

      // clean roots for targets for which rebuild is forced
      Tracer.Span cleanOutputSourcesSpan = Tracer.start("Clean output sources");
      cleanOutputRoots(context, JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) || forceCleanCaches);
      cleanOutputSourcesSpan.complete();

      Tracer.Span beforeTasksSpan = Tracer.start("'before' tasks");
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.running.before.tasks")));
      runTasks(context, myBuilderRegistry.getBeforeTasks());
      TimingLog.LOG.debug("'before' tasks finished");
      beforeTasksSpan.complete();

      Tracer.Span checkingSourcesSpan = Tracer.start("Building targets");
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.checking.sources")));
      buildChunks(context, buildProgress);
      TimingLog.LOG.debug("Building targets finished");
      checkingSourcesSpan.complete();

      Tracer.Span afterTasksSpan = Tracer.start("'after' span");
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.running.after.tasks")));
      runTasks(context, myBuilderRegistry.getAfterTasks());
      TimingLog.LOG.debug("'after' tasks finished");
      sendElapsedTimeMessages(context);
      afterTasksSpan.complete();
    }
    finally {
      if (buildProgress != null) {
        buildProgress.updateExpectedAverageTime();
        if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) && !Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
          myProjectDescriptor.dataManager.getTargetStateManager().setLastSuccessfulRebuildDuration(buildProgress.getAbsoluteBuildTime());
        }
      }
      for (TargetBuilder<?, ?> builder : myBuilderRegistry.getTargetBuilders()) {
        builder.buildFinished(context);
      }
      for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
        builder.buildFinished(context);
      }
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.finished.saving.caches")));
    }
  }

  private void sendElapsedTimeMessages(CompileContext context) {
    myElapsedTimeNanosByBuilder.entrySet()
      .stream()
      .map(entry -> {
        AtomicInteger processedSourcesRef = myNumberOfSourcesProcessedByBuilder.get(entry.getKey());
        int processedSources = processedSourcesRef != null ? processedSourcesRef.get() : 0;
        return new BuilderStatisticsMessage(entry.getKey().getPresentableName(), processedSources, entry.getValue().get()/1_000_000);
      })
      .sorted(Comparator.comparing(BuilderStatisticsMessage::getBuilderName))
      .forEach(context::processMessage);
  }

  private boolean runBuildersForChunk(final CompileContext context, final BuildTargetChunk chunk, BuildProgress buildProgress) throws ProjectBuildException, IOException {
    Set<? extends BuildTarget<?>> targets = chunk.getTargets();
    if (targets.size() > 1) {
      Set<ModuleBuildTarget> moduleTargets = new LinkedHashSet<>();
      for (BuildTarget<?> target : targets) {
        if (target instanceof ModuleBuildTarget) {
          moduleTargets.add((ModuleBuildTarget)target);
        }
        else {
          final String targetsString = Strings.join(targets, target1 -> Introspector.decapitalize(target1.getPresentableName()), ", ");
          final String message = JpsBuildBundle.message("build.message.cannot.build.0.because.it.is.included.into.a.circular.dependency.1", StringUtil.decapitalize(target.getPresentableName()), targetsString);
          context.processMessage(new CompilerMessage("", BuildMessage.Kind.ERROR, message));
          return false;
        }
      }

      return runModuleLevelBuilders(wrapWithModuleInfoAppender(context, moduleTargets), new ModuleChunk(moduleTargets), buildProgress);
    }

    final BuildTarget<?> target = targets.iterator().next();
    if (target instanceof ModuleBuildTarget) {
      Set<ModuleBuildTarget> mbt = Set.of((ModuleBuildTarget)target);
      return runModuleLevelBuilders(wrapWithModuleInfoAppender(context, mbt), new ModuleChunk(mbt), buildProgress);
    }

    //noinspection unchecked
    completeRecompiledSourcesSet(context, (Collection<? extends BuildTarget<BuildRootDescriptor>>)targets);

    // In general the set of files corresponding to changed source file may be different
    // Need this for example, to keep up with case changes in file names  for case-insensitive OSes:
    // deleting the output before copying is the only way to ensure the case of the output file's name is exactly the same as source file's case
    cleanOldOutputs(context, target);

    final List<TargetBuilder<?, ?>> builders = BuilderRegistry.getInstance().getTargetBuilders();
    int builderCount = 0;
    for (TargetBuilder<?, ?> builder : builders) {
      buildTarget(target, context, builder);
      builderCount++;
      buildProgress.updateProgress(target, ((double)builderCount)/builders.size(), context);
    }
    return true;
  }

  private @NotNull CompileContextImpl createContext(@NotNull CompileScope scope) {
    CompileContextImpl context = new CompileContextImpl(scope, myProjectDescriptor, myMessageDispatcher, myBuilderParams, myCancelStatus);
    if (myIsTestMode) {
      Utils.TEST_MODE_KEY.set(context, Boolean.TRUE);
    }
    return context;
  }

  private void cleanOutputRoots(@NotNull CompileContext context, boolean cleanCaches) throws ProjectBuildException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    ProjectBuildException projectBuildException = null;

    var targetCleanup = new Consumer<BuildTarget<?>>() {
      final ExecutorService executor = SharedThreadPool.getInstance().createBoundedExecutor("IncProjectBuilder Output Cleanup Pool", MAX_BUILDER_THREADS);
      final List<Future<?>> tasks = new ArrayList<>();
      @Override
      public void accept(BuildTarget<?> target) {
        if (SYNC_DELETE) {
          clearOutputFilesUninterruptibly(context, target);
        }
        else {
          tasks.add(executor.submit(() -> clearOutputFilesUninterruptibly(context, target)));
        }
      }

      void waitForTasks() {
        for (Future<?> task : tasks) {
          try {
            task.get();
          }
          catch (Throwable e) {
            LOG.info(e);
          }
        }
      }
    };

    final long cleanStart = System.nanoTime();
    try {
      final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(projectDescriptor.getProject());
      if (configuration.isClearOutputDirectoryOnRebuild()) {
        clearOutputs(context, targetCleanup);
      }
      else {
        for (BuildTarget<?> target : projectDescriptor.getBuildTargetIndex().getAllTargets()) {
          context.checkCanceled();
          if (context.getScope().isBuildForced(target)) {
            targetCleanup.accept(target);
          }
        }
      }

      for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
        if (context.getScope().isAllTargetsOfTypeAffected(type)) {
          cleanOutputOfStaleTargets(type, context);
        }
      }
    }
    catch (CompletionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ProjectBuildException) {
        projectBuildException = (ProjectBuildException)cause;
      }
      else {
        throw e;
      }
    }
    catch (ProjectBuildException e) {
      projectBuildException = e;
    }
    finally {
      targetCleanup.waitForTasks();

      LOG.info("Cleaned output directories in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cleanStart) + " ms");
      if (cleanCaches) {
        try {
          projectDescriptor.dataManager.clean(myAsyncTasks::add);
        }
        catch (IOException e) {
          if (projectBuildException == null) {
            projectBuildException = new ProjectBuildException(JpsBuildBundle.message("build.message.error.cleaning.compiler.storages"), e);
          }
          else {
            LOG.info("Error cleaning compiler storages", e);
          }
        }
        finally {
          projectDescriptor.fsState.clearAll();
          if (projectBuildException != null) {
            throw projectBuildException;
          }
        }
      }
      else {
        BuildTargetStateManager targetStateManager = projectDescriptor.dataManager.getTargetStateManager();
        for (BuildTarget<?> target : getTargetsWithClearedOutput(context)) {
          // This will ensure the target will be fully rebuilt either in this or in the future build session.
          // if this build fails or is cancelled, all such targets will still be marked as needing recompilation
          targetStateManager.invalidate(target);
        }
      }
    }
  }

  private void cleanOutputOfStaleTargets(BuildTargetType<?> targetType, CompileContext context) {
    BuildDataManager dataManager = myProjectDescriptor.dataManager;
    List<Pair<String, Integer>> targetIds = dataManager.getTargetStateManager().getStaleTargetIds(targetType);
    if (targetIds.isEmpty()) {
      return;
    }

    context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.cleaning.old.output.directories")));
    for (Pair<String, Integer> ids : targetIds) {
      String targetId = ids.first;
      try {
        SourceToOutputMappingImpl mapping = null;
        try {
          mapping = dataManager.createSourceToOutputMapForStaleTarget(targetType, targetId);
          clearOutputFiles(context, mapping, targetType, ids.second);
        }
        finally {
          if (mapping != null) {
            mapping.close();
          }
        }
        dataManager.cleanStaleTarget(targetType, targetId);
      }
      catch (IOException e) {
        LOG.warn(e);
        myMessageDispatcher.processMessage(new CompilerMessage("", BuildMessage.Kind.WARNING,
                                                               JpsBuildBundle.message("build.message.failed.to.delete.output.files.from.obsolete.0.target.1",
                                                                                      targetId, e.toString())));
      }
    }
  }

  public static void clearOutputFiles(CompileContext context, BuildTarget<?> target) throws IOException {
    final SourceToOutputMapping map = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
    BuildTargetType<?> targetType = target.getTargetType();
    clearOutputFiles(context, map, targetType, context.getProjectDescriptor().dataManager.getTargetStateManager().getBuildTargetId(target));
    registerTargetsWithClearedOutput(context, Collections.singletonList(target));
  }

  @SuppressWarnings("SSBasedInspection")
  private boolean processDeletedPaths(CompileContext context, final Set<? extends BuildTarget<?>> targets) throws ProjectBuildException {
    boolean doneSomething = false;
    try {
      // cleanup outputs
      final Map<BuildTarget<?>, Collection<Path>> targetToRemovedSources = new HashMap<>();

      Set<Path> dirsToDelete = FileCollectionFactory.createCanonicalPathSet();
      for (BuildTarget<?> target : targets) {
        Collection<String> deletedPaths = myProjectDescriptor.fsState.getAndClearDeletedPaths(target);
        if (deletedPaths.isEmpty()) {
          continue;
        }

        targetToRemovedSources.put(target, deletedPaths.stream().map(Path::of).collect(Collectors.toList()));
        if (isTargetOutputCleared(context, target)) {
          continue;
        }

        int buildTargetId = context.getProjectDescriptor().dataManager.getTargetStateManager().getBuildTargetId(target);
        final boolean shouldPruneEmptyDirs = target instanceof ModuleBasedTarget;
        BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
        final SourceToOutputMapping sourceToOutputStorage = dataManager.getSourceToOutputMap(target);
        final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        // actually delete outputs associated with removed paths
        Collection<String> pathsForIteration;
        if (myIsTestMode) {
          // ensure predictable order in test logs
          pathsForIteration = new ArrayList<>(deletedPaths);
          Collections.sort((List<String>)pathsForIteration);
        }
        else {
          pathsForIteration = deletedPaths;
        }
        for (String deletedSource : pathsForIteration) {
          // deleting outputs corresponding to a non-existing source
          Collection<String> outputs = sourceToOutputStorage.getOutputs(deletedSource);
          if (outputs != null && !outputs.isEmpty()) {
            List<String> deletedOutputPaths = new ArrayList<>();
            OutputToTargetMapping outputToSourceRegistry = dataManager.getOutputToTargetMapping();
            for (String output : outputToSourceRegistry.removeTargetAndGetSafeToDeleteOutputs(outputs, buildTargetId, sourceToOutputStorage)) {
              boolean deleted = BuildOperations.deleteRecursivelyAndCollectDeleted(Path.of(output), deletedOutputPaths, shouldPruneEmptyDirs ? dirsToDelete : null);
              if (deleted) {
                doneSomething = true;
              }
            }
            if (!deletedOutputPaths.isEmpty()) {
              if (logger.isEnabled()) {
                logger.logDeletedFiles(deletedOutputPaths);
              }
              context.processMessage(new FileDeletedEvent(deletedOutputPaths));
            }
          }

          if (target instanceof ModuleBuildTarget) {
            // check if the deleted source was associated with a form
            OneToManyPathMapping sourceToFormMap = dataManager.getSourceToFormMap(target);
            Path deletedSourceFile = Path.of(deletedSource);
            Collection<Path> boundForms = sourceToFormMap.getOutputs(deletedSourceFile);
            if (boundForms != null) {
              for (Path formFile : boundForms) {
                if (Files.exists(formFile)) {
                  FSOperations.markDirty(context, CompilationRound.CURRENT, formFile.toFile());
                }
              }
              sourceToFormMap.remove(deletedSourceFile);
            }
          }
        }
      }
      if (!targetToRemovedSources.isEmpty()) {
        Map<BuildTarget<?>, Collection<Path>> existing = Utils.REMOVED_SOURCES_KEY.get(context);
        if (existing != null) {
          for (Map.Entry<BuildTarget<?>, Collection<Path>> entry : existing.entrySet()) {
            Collection<Path> paths = targetToRemovedSources.get(entry.getKey());
            if (paths == null) {
              targetToRemovedSources.put(entry.getKey(), entry.getValue());
            }
            else {
              paths.addAll(entry.getValue());
            }
          }
        }
        Utils.REMOVED_SOURCES_KEY.set(context, targetToRemovedSources);
      }

      FSOperations.pruneEmptyDirs(context, dirsToDelete);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
    return doneSomething;
  }

  private static void registerTargetsWithClearedOutput(CompileContext context, Collection<? extends BuildTarget<?>> targets) {
    synchronized (TARGET_WITH_CLEARED_OUTPUT) {
      Set<BuildTarget<?>> data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT);
      if (data == null) {
        data = new HashSet<>();
        context.putUserData(TARGET_WITH_CLEARED_OUTPUT, data);
      }
      data.addAll(targets);
    }
  }

  private static boolean isTargetOutputCleared(CompileContext context, BuildTarget<?> target) {
    synchronized (TARGET_WITH_CLEARED_OUTPUT) {
      Set<BuildTarget<?>> data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT);
      return data != null && data.contains(target);
    }
  }

  private static @Unmodifiable Set<BuildTarget<?>> getTargetsWithClearedOutput(@NotNull CompileContext context) {
    synchronized (TARGET_WITH_CLEARED_OUTPUT) {
      Set<BuildTarget<?>> data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT);
      return data == null ? Collections.emptySet() : Set.copyOf(data);
    }
  }

  private enum Applicability {
    NONE, PARTIAL, ALL;

    static <T> Applicability calculate(Predicate<? super T> p, Collection<? extends T> collection) {
      int count = 0;
      int item = 0;
      for (T elem : collection) {
        item++;
        if (p.test(elem)) {
          count++;
          if (item > count) {
            return PARTIAL;
          }
        }
        else {
          if (count > 0) {
            return PARTIAL;
          }
        }
      }
      return count == 0? NONE : ALL;
    }
  }

  private void clearOutputs(@NotNull CompileContext context, @NotNull Consumer<BuildTarget<?>> targetCleanup) throws ProjectBuildException {
    MultiMap<File, BuildTarget<?>> rootsToDelete = MultiMap.createSet();
    Set<File> allSourceRoots = FileCollectionFactory.createCanonicalFileSet();

    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    final List<? extends BuildTarget<?>> allTargets = projectDescriptor.getBuildTargetIndex().getAllTargets();
    for (BuildTarget<?> target : allTargets) {
      if (target instanceof ModuleBasedTarget) {
        for (File file : target.getOutputRoots(context)) {
          rootsToDelete.putValue(file, target);
        }
      }
      else if (context.getScope().isBuildForced(target)) {
        targetCleanup.accept(target);
      }
    }

    final ModuleExcludeIndex moduleIndex = projectDescriptor.getModuleExcludeIndex();
    for (BuildTarget<?> target : allTargets) {
      for (BuildRootDescriptor descriptor : projectDescriptor.getBuildRootIndex().getTargetRoots(target, context)) {
        // excluding from checks roots with generated sources; because it is safe to delete generated stuff
        if (!descriptor.isGenerated()) {
          File rootFile = descriptor.getRootFile();
          // Some roots aren't marked by as generated, but in fact they are produced by some builder, and it's safe to remove them.
          // However, if a root isn't excluded, it means that its content will be shown in 'Project View'
          // and a user can create new files under it, so it would be dangerous to clean such roots
          if (moduleIndex.isInContent(rootFile)) {
            allSourceRoots.add(rootFile);
          }
        }
      }
    }

    // check that output and source roots are not overlapping
    final CompileScope compileScope = context.getScope();
    final List<File> filesToDelete = new ArrayList<>();
    final Predicate<BuildTarget<?>> forcedBuild = compileScope::isBuildForced;
    for (Map.Entry<File, Collection<BuildTarget<?>>> entry : rootsToDelete.entrySet()) {
      context.checkCanceled();
      final File outputRoot = entry.getKey();
      final Collection<BuildTarget<?>> rootTargets = entry.getValue();
      final Applicability applicability = Applicability.calculate(forcedBuild, rootTargets);
      if (applicability == Applicability.NONE) {
        continue;
      }
      // It makes no sense to delete already empty root, but instead it makes sense to cleanup the target, because there may exist
      // a directory that has been previously the output root for the target
      boolean okToDelete = applicability == Applicability.ALL && !isEmpty(outputRoot);
      if (okToDelete && !moduleIndex.isExcluded(outputRoot)) {
        // if output root itself is directly or indirectly excluded,
        // there cannot be any manageable sources under it, even if the output root is located under some source root
        // so in this case it is safe to delete such root
        if (JpsPathUtil.isUnder(allSourceRoots, outputRoot)) {
          okToDelete = false;
        }
        else {
          final Set<File> _outRoot = FileCollectionFactory.createCanonicalFileSet(List.of(outputRoot));
          for (File srcRoot : allSourceRoots) {
            if (JpsPathUtil.isUnder(_outRoot, srcRoot)) {
              okToDelete = false;
              break;
            }
          }
        }
        if (!okToDelete) {
          context.processMessage(new CompilerMessage(
            "", BuildMessage.Kind.WARNING,
            JpsBuildBundle.message("build.message.output.path.0.intersects.with.a.source.root", outputRoot.getPath()))
          );
        }
      }

      if (okToDelete) {
        // do not delete output root itself to avoid lots of unnecessary "roots_changed" events in IDEA
        final File[] children = outputRoot.listFiles();
        if (children != null) {
          for (File child : children) {
            if (!child.delete()) {
              filesToDelete.add(child);
            }
          }
        }
        else { // the output root must be a file
          if (!outputRoot.delete()) {
            filesToDelete.add(outputRoot);
          }
        }
        registerTargetsWithClearedOutput(context, rootTargets);
      }
      else {
        context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.cleaning.output.directories")));
        // clean only those files we are aware of
        for (BuildTarget<?> target : rootTargets) {
          if (compileScope.isBuildForced(target)) {
            targetCleanup.accept(target);
          }
        }
      }
    }

    if (!filesToDelete.isEmpty()) {
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.cleaning.output.directories")));
      if (SYNC_DELETE) {
        for (var file : filesToDelete) {
          context.checkCanceled();
          FileUtilRt.delete(file);
        }
      }
      else {
        myAsyncTasks.add(FileUtil.asyncDelete(filesToDelete));
      }
    }
  }

  private static boolean isEmpty(@NotNull File outputRoot) {
    String[] files = outputRoot.list();
    return files == null || files.length == 0;
  }

  private static void clearOutputFilesUninterruptibly(CompileContext context, BuildTarget<?> target) {
    try {
      clearOutputFiles(context, target);
    }
    catch (Throwable e) {
      LOG.info(e);
      String reason = e.getMessage();
      if (reason == null) {
        reason = e.getClass().getName();
      }
      context.processMessage(new CompilerMessage("", BuildMessage.Kind.WARNING, JpsBuildBundle
        .message("build.message.problems.clearing.output.files.for.target.0.1", target.getPresentableName(), reason)));
    }
  }

  private static void runTasks(@NotNull CompileContext context, @NotNull List<? extends BuildTask> tasks) throws ProjectBuildException {
    for (BuildTask task : tasks) {
      task.build(context);
    }
  }

  private void buildChunks(@NotNull CompileContextImpl context, @NotNull BuildProgress buildProgress) throws ProjectBuildException {
    try {
      boolean compileInParallel = isParallelBuild();
      if (compileInParallel && MAX_BUILDER_THREADS <= 1) {
        LOG.info("Switched off parallel compilation because maximum number of builder threads is less than 2. Set '"
                 + GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION + "' system property to a value greater than 1 to really enable parallel compilation.");
        compileInParallel = false;
      }

      Tracer.Span buildSpan = Tracer.start(compileInParallel ? "Parallel build" : "Non-parallel build");
      if (compileInParallel) {
        new BuildParallelizer(context, buildProgress).buildInParallel();
      }
      else {
        // non-parallel build
        ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
        BuildDataManager dataManager = projectDescriptor.dataManager;
        final Runnable flushCommand = Utils.asCountedRunnable(FLUSH_INVOCATIONS_TO_SKIP, () -> dataManager.flush(true));
        for (BuildTargetChunk chunk : projectDescriptor.getBuildTargetIndex().getSortedTargetChunks(context)) {
          try {
            buildChunkIfAffected(context, context.getScope(), chunk, buildProgress);
          }
          finally {
            dataManager.closeSourceToOutputStorages(chunk.getTargets());
            flushCommand.run();
          }
        }
      }
      buildSpan.complete();
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static final class BuildChunkTask {
    private final BuildTargetChunk myChunk;
    private final AtomicInteger myNotBuildDependenciesCount = new AtomicInteger(0);
    private final Set<BuildChunkTask> myNotBuiltDependencies = new HashSet<>();
    private final List<BuildChunkTask> myTasksDependsOnThis = new ArrayList<>();
    private int mySelfScore = 0;
    private int myDepsScore = 0;
    private int myIndex = 0;

    private BuildChunkTask(BuildTargetChunk chunk) {
      myChunk = chunk;
    }

    private int getScore() {
      return myDepsScore + mySelfScore;
    }

    public BuildTargetChunk getChunk() {
      return myChunk;
    }

    public boolean isReady() {
      return myNotBuildDependenciesCount.get() == 0;
    }

    public void addDependency(BuildChunkTask dependency) {
      if (myNotBuiltDependencies.add(dependency)) {
        myNotBuildDependenciesCount.incrementAndGet();
        dependency.myTasksDependsOnThis.add(this);
      }
    }

     @Nullable List<BuildChunkTask> getNextReadyTasks() {
      List<BuildChunkTask> nextTasks = null;
      for (BuildChunkTask task : myTasksDependsOnThis) {
        int dependenciesCount = task.myNotBuildDependenciesCount.decrementAndGet();
        if (dependenciesCount == 0) {
          if (nextTasks == null) {
            nextTasks = new SmartList<>();
          }
          nextTasks.add(task);
        }
      }
      return nextTasks;
    }
  }

  private final class BuildParallelizer {
    private final CompileContext myContext;
    private final BuildProgress myBuildProgress;
    private final AtomicReference<Throwable> myException = new AtomicReference<>();
    private final CountDownLatch taskCountDown;
    private final List<BuildChunkTask> myTasks;
    private final Runnable myFlushCommand;

    private BuildParallelizer(CompileContext context, BuildProgress buildProgress) {
      Tracer.Span span = Tracer.start("BuildParallelizer constructor");
      myContext = context;
      myBuildProgress = buildProgress;
      final ProjectDescriptor pd = myContext.getProjectDescriptor();
      final BuildTargetIndex targetIndex = pd.getBuildTargetIndex();
      myFlushCommand = Utils.asCountedRunnable(FLUSH_INVOCATIONS_TO_SKIP, () -> pd.dataManager.flush(true));

      List<BuildTargetChunk> chunks = targetIndex.getSortedTargetChunks(myContext);
      myTasks = new ArrayList<>(chunks.size());
      Map<BuildTarget<?>, BuildChunkTask> targetToTask = new HashMap<>(chunks.size());
      for (BuildTargetChunk chunk : chunks) {
        BuildChunkTask task = new BuildChunkTask(chunk);
        myTasks.add(task);
        for (BuildTarget<?> target : chunk.getTargets()) {
          targetToTask.put(target, task);
        }
        task.mySelfScore = chunk.getTargets().size();
      }

      Tracer.Span collectTaskDependantsSpan = Tracer.start("IncProjectBuilder.collectTaskDependants");
      int taskCounter = 0;
      for (BuildChunkTask task : myTasks) {
        task.myIndex = taskCounter;
        taskCounter++;
        for (BuildTarget<?> target : task.getChunk().getTargets()) {
          for (BuildTarget<?> dependency : targetIndex.getDependencies(target, myContext)) {
            BuildChunkTask depTask = targetToTask.get(dependency);
            if (depTask != null && depTask != task) {
              task.addDependency(depTask);
            }
          }
        }
      }
      collectTaskDependantsSpan.complete();

      Tracer.Span prioritisationSpan = Tracer.start("IncProjectBuilder.prioritisation");
      // bitset stores indexes of transitively dependant tasks
      HashMap<BuildChunkTask, BitSet> chunkToTransitive = new HashMap<>();
      for (int i = myTasks.size() - 1; i >= 0; i--) {
        BuildChunkTask task = myTasks.get(i);
        List<BuildChunkTask> dependantTasks = task.myTasksDependsOnThis;
        Set<BuildChunkTask> directDependants = new HashSet<>(dependantTasks);
        BitSet transitiveDependants = new BitSet();
        for (BuildChunkTask directDependant : directDependants) {
          BitSet dependantChunkTransitiveDependants = chunkToTransitive.get(directDependant);
          if (dependantChunkTransitiveDependants != null) {
            transitiveDependants.or(dependantChunkTransitiveDependants);
            transitiveDependants.set(directDependant.myIndex);
          }
        }
        chunkToTransitive.put(task, transitiveDependants);
        task.myDepsScore = transitiveDependants.cardinality();
      }
      prioritisationSpan.complete();

      taskCountDown = new CountDownLatch(myTasks.size());
      span.complete();
    }

    public void buildInParallel() throws ProjectBuildException {
      List<BuildChunkTask> initialTasks = new ArrayList<>();
      for (BuildChunkTask task : myTasks) {
        if (task.isReady()) {
          initialTasks.add(task);
        }
      }

      Executor parallelBuildExecutor = SharedThreadPool.getInstance().createCustomPriorityQueueBoundedExecutor(
        "IncProjectBuilder Executor Pool",
        MAX_BUILDER_THREADS,
        (o1, o2) -> {
          int p1 = o1 instanceof RunnableWithPriority ? ((RunnableWithPriority)o1).priority : 1;
          int p2 = o1 instanceof RunnableWithPriority ? ((RunnableWithPriority)o2).priority : 1;
          return Integer.compare(p2, p1);
        });

      queueTasks(initialTasks, LOG.isDebugEnabled(), parallelBuildExecutor);
      try {
        taskCountDown.await();
      }
      catch (InterruptedException e) {
        LOG.info(e);
      }

      final Throwable throwable = myException.get();
      if (throwable instanceof ProjectBuildException) {
        throw (ProjectBuildException)throwable;
      }
      else if (throwable != null) {
        throw new ProjectBuildException(throwable);
      }
    }

    private void queueTasks(List<BuildChunkTask> tasks, boolean isDebugLogEnabled, @NotNull Executor parallelBuildExecutor) {
      BuildChunkTask[] sorted = tasks.toArray(new BuildChunkTask[0]);
      Arrays.sort(sorted, Comparator.comparingLong(BuildChunkTask::getScore).reversed());

      if (isDebugLogEnabled) {
        List<BuildTargetChunk> chunksToLog = new ArrayList<>(sorted.length);
        for (BuildChunkTask task : sorted) {
          chunksToLog.add(task.getChunk());
        }
        StringBuilder logBuilder = new StringBuilder().append("Queuing ").append(chunksToLog.size()).append(" chunks in parallel: ");
        chunksToLog.sort(Comparator.comparing(BuildTargetChunk::toString));
        for (BuildTargetChunk chunk : chunksToLog) {
          logBuilder.append(chunk.toString()).append("; ");
        }
        LOG.debug(logBuilder.toString());
      }

      for (BuildChunkTask task : sorted) {
        queueTask(task, isDebugLogEnabled, parallelBuildExecutor);
      }
    }

    private abstract class RunnableWithPriority implements Runnable, ContextAwareRunnable {
      public final int priority;

      RunnableWithPriority(int priority) {
        this.priority = priority;
      }
    }

    private void queueTask(@NotNull BuildChunkTask task, boolean isDebugLogEnabled, @NotNull Executor parallelBuildExecutor) {
      CompileContext chunkLocalContext = createContextWrapper(myContext);
      parallelBuildExecutor.execute(new RunnableWithPriority(task.getScore()) {
        @Override
        public void run() {
          try {
            try {
              if (myException.get() == null) {
                buildChunkIfAffected(chunkLocalContext, myContext.getScope(), task.getChunk(), myBuildProgress);
              }
            }
            finally {
              myProjectDescriptor.dataManager.closeSourceToOutputStorages(task.getChunk().getTargets());
              myFlushCommand.run();
            }
          }
          catch (Throwable e) {
            myException.compareAndSet(null, e);
            LOG.info(e);
          }
          finally {
            try {
              if (isDebugLogEnabled) {
                LOG.debug("Finished compilation of " + task.getChunk().toString());
              }

              List<BuildChunkTask> nextTasks = task.getNextReadyTasks();
              if (nextTasks != null && !nextTasks.isEmpty()) {
                queueTasks(nextTasks, isDebugLogEnabled, parallelBuildExecutor);
              }
            }
            finally {
              taskCountDown.countDown();
            }
          }
        }
      });
    }
  }

  private void buildChunkIfAffected(CompileContext context, CompileScope scope, BuildTargetChunk chunk,
                                    BuildProgress buildProgress) throws ProjectBuildException {
    Tracer.Span isAffectedSpan = Tracer.start("isAffected");
    boolean affected = isAffected(scope, chunk);
    isAffectedSpan.complete();
    if (affected) {
      buildTargetsChunk(context, chunk, buildProgress);
    }
  }

  private static boolean isAffected(CompileScope scope, BuildTargetChunk chunk) {
    for (BuildTarget<?> target : chunk.getTargets()) {
      if (scope.isAffected(target)) {
        return true;
      }
    }
    return false;
  }

  private <R extends BuildRootDescriptor, T extends BuildTarget<R>> void buildTarget(T target,
                                                                                     CompileContext context,
                                                                                     TargetBuilder<?, ?> builder)
    throws ProjectBuildException, IOException {

    if (builder.getTargetTypes().contains(target.getTargetType())) {
      DirtyFilesHolder<R, T> holder = new DirtyFilesHolderBase<>(context) {
        @Override
        public void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException {
          context.getProjectDescriptor().fsState.processFilesToRecompile(context, target, processor);
        }
      };
      BuildOutputConsumerImpl outputConsumer = new BuildOutputConsumerImpl(target, context);
      long start = System.nanoTime();
      ((TargetBuilder<R, T>)builder).build(target, holder, outputConsumer, context);
      storeBuilderStatistics(builder, System.nanoTime() - start, outputConsumer.getNumberOfProcessedSources());
      outputConsumer.fireFileGeneratedEvent();
      context.checkCanceled();
    }
  }

  private static CompileContext wrapWithModuleInfoAppender(CompileContext context, Collection<ModuleBuildTarget> moduleTargets) {
    final Class<MessageHandler> messageHandlerInterface = MessageHandler.class;
    return ReflectionUtil.proxy(context.getClass().getClassLoader(), CompileContext.class, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args != null && args.length > 0 && messageHandlerInterface.equals(method.getDeclaringClass())) {
          for (Object arg : args) {
            if (arg instanceof CompilerMessage) {
              final CompilerMessage compilerMessage = (CompilerMessage)arg;
              for (ModuleBuildTarget target : moduleTargets) {
                compilerMessage.addModuleName(target.getModule().getName());
              }
              break;
            }
          }
        }
        final MethodHandle mh = ourLookup.unreflect(method);
        return args == null? mh.invoke(context) : mh.bindTo(context).asSpreader(Object[].class, args.length).invoke(args);
      }
    });
  }

  /**
     if an output file is generated from multiple sources, make sure all of them are added for recompilation
   */
  private static <T extends BuildTarget<R>, R extends BuildRootDescriptor> void completeRecompiledSourcesSet(CompileContext context, Collection<T> targets) throws IOException {
    final CompileScope scope = context.getScope();
    for (T target : targets) {
      if (scope.isBuildForced(target)) {
        return; // assuming build is either forced for all targets in a chunk or for none of them
      }
    }

    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    final Set<String> affectedOutputs = CollectionFactory.createFilePathSet();
    final Set<String> affectedSources = CollectionFactory.createFilePathSet();

    final List<SourceToOutputMapping> mappings = new ArrayList<>();
    for (T target : targets) {
      projectDescriptor.fsState.processFilesToRecompile(context, target, new FileProcessor<>() {
        private SourceToOutputMapping srcToOut;
        @Override
        public boolean apply(@NotNull T target, @NotNull File file, @NotNull R root) throws IOException {
          String src = FileUtilRt.toSystemIndependentName(file.getPath());
          if (!affectedSources.add(src)) {
            return true;
          }

          if (srcToOut == null) { // lazy init
            srcToOut = projectDescriptor.dataManager.getSourceToOutputMap(target);
            mappings.add(srcToOut);
          }

          Collection<Path> outs = srcToOut.getOutputs(file.toPath());
          if (outs != null) {
            // Temporary hack for KTIJ-197
            // Change of only one input of *.kotlin_module files didn't trigger recompilation of all inputs in old behaviour.
            // Now it does. It isn't yet obvious whether it is right or wrong behaviour. Let's leave old behaviour for a
            // while for safety and keeping kotlin incremental JPS tests green
            List<String> filteredOuts = new ArrayList<>(outs.size());
            for (Path out : outs) {
              String outPath = FileUtilRt.toSystemIndependentName(out.toString());
              if (!outPath.endsWith(".kotlin_module")) {
                filteredOuts.add(outPath);
              }
            }
            affectedOutputs.addAll(filteredOuts);
          }
          return true;
        }
      });
    }

    if (!affectedOutputs.isEmpty()) {
      for (SourceToOutputMapping srcToOut : mappings) {
        for (SourceToOutputMappingCursor cursor = srcToOut.cursor(); cursor.hasNext(); ) {
          String src = cursor.next();
          if (!affectedSources.contains(src)) {
            for (String out : cursor.getOutputPaths()) {
              if (affectedOutputs.contains(out)) {
                FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, Path.of(src));
                break;
              }
            }
          }
        }
      }
    }

  }

  // return true if changed something, false otherwise
  private boolean runModuleLevelBuilders(final CompileContext context, final ModuleChunk chunk, BuildProgress buildProgress) throws ProjectBuildException, IOException {
    for (BuilderCategory category : BuilderCategory.values()) {
      for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
        builder.chunkBuildStarted(context, chunk);
      }
    }

    completeRecompiledSourcesSet(context, chunk.getTargets());

    boolean doneSomething = false;
    boolean rebuildFromScratchRequested = false;
    boolean nextPassRequired;
    int roundCount = -1;
    boolean isFullRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);
    ChunkBuildOutputConsumerImpl outputConsumer = new ChunkBuildOutputConsumerImpl(context);
    try {
      do {
        nextPassRequired = false;
        roundCount += 1;
        myProjectDescriptor.fsState.beforeNextRoundStart(context, chunk);

        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder = new DirtyFilesHolderBase<>(context) {
          @Override
          public void processDirtyFiles(@NotNull FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget> processor)
            throws IOException {
            FSOperations.processFilesToRecompile(context, chunk, processor);
          }
        };
        if (!isFullRebuild) {
          Map<ModuleBuildTarget, Map<Path, List<Path>>> cleanedSources =
            BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder);
          for (Map.Entry<ModuleBuildTarget, Map<Path, List<Path>>> entry : cleanedSources.entrySet()) {
            ModuleBuildTarget target = entry.getKey();
            Set<Path> files = entry.getValue().keySet();
            if (!files.isEmpty()) {
              SourceToOutputMapping mapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
              for (Path sourceFile : files) {
                List<Path> outputs = entry.getValue().get(sourceFile);
                mapping.setOutputs(sourceFile, outputs);
                if (!outputs.isEmpty()) {
                  LOG.info("Some outputs were not removed for " + sourceFile + " source file: " + outputs);
                }
              }
            }
          }
        }

        try {
          int buildersPassed = 0;
          BUILDER_CATEGORY_LOOP:
          for (BuilderCategory category : BuilderCategory.values()) {
            final List<ModuleLevelBuilder> builders = myBuilderRegistry.getBuilders(category);
            if (category == BuilderCategory.CLASS_POST_PROCESSOR) {
              // ensure changes from instrumenters are visible to class post-processors
              saveInstrumentedClasses(outputConsumer);
            }
            if (builders.isEmpty()) {
              continue;
            }

            try {
              for (ModuleLevelBuilder builder : builders) {
                outputConsumer.setCurrentBuilderName(builder.getPresentableName());
                processDeletedPaths(context, chunk.getTargets());
                long start = System.nanoTime();
                int processedSourcesBefore = outputConsumer.getNumberOfProcessedSources();
                ErrorsCapture errCapture = null;
                ModuleLevelBuilder.ExitCode buildResult = ModuleLevelBuilder.ExitCode.NOTHING_DONE;
                try {
                  buildResult = builder.build(roundCount == 0 && !isFullRebuild? errCapture = ErrorsCapture.wrap(context) : context, chunk, dirtyFilesHolder, outputConsumer);
                }
                catch (StopBuildException e) {
                  if (errCapture != null) {
                    buildResult = ModuleLevelBuilder.ExitCode.ABORT;
                  }
                  else {
                    throw e;
                  }
                }
                finally {
                  storeBuilderStatistics(builder, System.nanoTime() - start, outputConsumer.getNumberOfProcessedSources() - processedSourcesBefore);
                  doneSomething |= (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE);
                }

                context.checkCanceled();

                if (errCapture != null && (errCapture.hasErrors() || buildResult == ModuleLevelBuilder.ExitCode.ABORT)) {
                  // attempt recovery
                  if (JavaBuilderUtil.updateMappingsOnRoundCompletion(errCapture, dirtyFilesHolder, chunk)) {
                    // mark current dirty files for the next round
                    dirtyFilesHolder.processDirtyFiles((target, file, root) -> {
                      FSOperations.markDirty(context, CompilationRound.NEXT, file);
                      return true;
                    });
                    nextPassRequired = true;
                    break BUILDER_CATEGORY_LOOP;
                  }
                  else { // no additional files were marked
                    errCapture.reportErrors(); // report postponed errors
                  }
                }

                if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
                  throw new StopBuildException(JpsBuildBundle.message("build.message.builder.0.requested.build.stop", builder.getPresentableName()));
                }

                if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
                  nextPassRequired = true;
                }
                else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
                  if (!rebuildFromScratchRequested && !isFullRebuild) {
                    notifyChunkRebuildRequested(context, chunk, builder);
                    // allow rebuild from scratch only once per chunk
                    rebuildFromScratchRequested = true;
                    try {
                      // forcibly mark all files in the chunk dirty
                      context.getProjectDescriptor().fsState.clearContextRoundData(context);
                      FSOperations.markDirty(context, CompilationRound.NEXT, chunk, null);
                      // reverting to the beginning
                      nextPassRequired = true;
                      outputConsumer.clear();
                      break BUILDER_CATEGORY_LOOP;
                    }
                    catch (Exception e) {
                      throw new ProjectBuildException(e);
                    }
                  }
                  else {
                    LOG.debug("Builder " + builder.getPresentableName() + " requested second chunk rebuild");
                  }
                }

                buildersPassed++;
                for (ModuleBuildTarget target : chunk.getTargets()) {
                  buildProgress.updateProgress(target, ((double)buildersPassed) / myTotalModuleLevelBuilderCount, context);
                }
              }
            }
            finally {
              outputConsumer.setCurrentBuilderName(null);
            }
          }
        }
        finally {
          final boolean moreToCompile = JavaBuilderUtil.updateMappingsOnRoundCompletion(context, dirtyFilesHolder, chunk);
          if (moreToCompile) {
            nextPassRequired = true;
          }
          JavaBuilderUtil.clearDataOnRoundCompletion(context);
        }
      }
      while (nextPassRequired);
    }
    finally {
      saveInstrumentedClasses(outputConsumer);
      outputConsumer.fireFileGeneratedEvents();
      outputConsumer.clear();
      for (BuilderCategory category : BuilderCategory.values()) {
        for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
          builder.chunkBuildFinished(context, chunk);
        }
      }
      if (Utils.errorsDetected(context)) {
        context.processMessage(new CompilerMessage("", BuildMessage.Kind.JPS_INFO, JpsBuildBundle.message("build.message.errors.occurred.while.compiling.module.0", chunk.getPresentableShortName())));
      }
    }

    return doneSomething;
  }

  private interface ErrorsCapture extends CompileContext {
    boolean hasErrors();

    boolean reportErrors();

    static ErrorsCapture wrap(CompileContext delegate) {
      List<CompilerMessage> capturedErrors = new SmartList<>();
      return (ErrorsCapture)Proxy.newProxyInstance(ErrorsCapture.class.getClassLoader(), new Class[] {ErrorsCapture.class}, (proxy, method, args) -> {
        Class<?> declaringClass = method.getDeclaringClass();

        if (ErrorsCapture.class.equals(declaringClass)) { // self implementation
          boolean empty = capturedErrors.isEmpty();
          if ("hasErrors".equals(method.getName())) {
            return !empty;
          }
          // reportErrors impl
          if (empty) {
            return false;
          }
          for (CompilerMessage error : capturedErrors) {
            delegate.processMessage(error);
          }
          capturedErrors.clear();
          return true;
        }

        if (MessageHandler.class.equals(declaringClass)) { // capture implementation
          for (Object arg : args) {
            if (arg instanceof CompilerMessage) {
              CompilerMessage compilerMessage = (CompilerMessage)arg;
              if (compilerMessage.getKind() == BuildMessage.Kind.ERROR) {
                capturedErrors.add(compilerMessage);
                return null;
              }
            }
          }
        }

        if (UserDataHolder.class.equals(declaringClass) && args != null && args.length == 1 && !Void.class.equals(method.getReturnType()) && Utils.ERRORS_DETECTED_KEY.equals(args[0]) ) {
          // UserDataHolder.getUserData(ERRORS_DETECTED_KEY)
          if (!capturedErrors.isEmpty()) {
            return true;
          }
        }

        MethodHandle mh = ourLookup.unreflect(method).bindTo(delegate);
        return args == null? mh.invoke() : mh.asSpreader(Object[].class, args.length).invoke(args);  // delegate further
      });
    }
  }

  private static <T extends BuildRootDescriptor> void cleanOldOutputs(final CompileContext context, final BuildTarget<T> target) throws ProjectBuildException{
    if (!context.getScope().isBuildForced(target)) {
      BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, new DirtyFilesHolderBase<T, BuildTarget<T>>(context) {
        @Override
        public void processDirtyFiles(@NotNull FileProcessor<T, BuildTarget<T>> processor) throws IOException {
          context.getProjectDescriptor().fsState.processFilesToRecompile(context, target, processor);
        }
      });
    }
  }

  private void buildTargetsChunk(CompileContext context, BuildTargetChunk chunk, BuildProgress buildProgress) throws ProjectBuildException {
    Tracer.DelayedSpan buildSpan = Tracer.start(() -> "Building " + chunk.getPresentableName());
    final BuildFSState fsState = myProjectDescriptor.fsState;
    boolean doneSomething;
    try {
      context.setCompilationStartStamp(chunk.getTargets(), System.currentTimeMillis());

      sendBuildingTargetMessages(chunk.getTargets(), BuildingTargetProgressMessage.Event.STARTED);
      Utils.ERRORS_DETECTED_KEY.set(context, Boolean.FALSE);

      for (BuildTarget<?> target : chunk.getTargets()) {
        BuildOperations.ensureFSStateInitialized(context, target, false);
      }

      doneSomething = processDeletedPaths(context, chunk.getTargets());

      fsState.beforeChunkBuildStart(context, chunk.getTargets());

      Tracer.DelayedSpan runBuildersSpan = Tracer.start(() -> "runBuilders " + chunk.getPresentableName());
      doneSomething |= runBuildersForChunk(context, chunk, buildProgress);
      runBuildersSpan.complete();

      fsState.clearContextRoundData(context);
      fsState.clearContextChunk(context);

      if (doneSomething) {
        BuildOperations.markTargetsUpToDate(context, chunk.getTargets());
      }
    }
    catch (BuildDataCorruptedException | ProjectBuildException e) {
      throw e;
    }
    catch (Throwable e) {
      @NlsSafe StringBuilder message = new StringBuilder();
      message.append(chunk.getPresentableName()).append(": ").append(e.getClass().getName());
      final String exceptionMessage = e.getMessage();
      if (exceptionMessage != null) {
        message.append(": ").append(exceptionMessage);
      }
      throw new ProjectBuildException(message.toString(), e);
    }
    finally {
      buildProgress.onTargetChunkFinished(chunk.getTargets(), context);
      for (BuildRootDescriptor descriptor : myProjectDescriptor.getBuildRootIndex().clearTempRoots(context)) {
        myProjectDescriptor.fsState.clearRecompile(descriptor);
      }
      try {
        // restore deleted paths that were not processed by 'integrate'
        Map<BuildTarget<?>, Collection<Path>> map = Utils.REMOVED_SOURCES_KEY.get(context);
        if (map != null) {
          for (Map.Entry<BuildTarget<?>, Collection<Path>> entry : map.entrySet()) {
            BuildTarget<?> target = entry.getKey();
            Collection<Path> paths = entry.getValue();
            if (paths != null) {
              for (Path file : paths) {
                fsState.registerDeleted(context, target, file, null);
              }
            }
          }
        }
      }
      catch (IOException e) {
        //noinspection ThrowFromFinallyBlock
        throw new ProjectBuildException(e);
      }
      finally {
        Utils.REMOVED_SOURCES_KEY.set(context, null);
        sendBuildingTargetMessages(chunk.getTargets(), BuildingTargetProgressMessage.Event.FINISHED);
        buildSpan.complete();
      }
    }
  }

  private void sendBuildingTargetMessages(@NotNull Set<? extends BuildTarget<?>> targets, @NotNull BuildingTargetProgressMessage.Event event) {
    myMessageDispatcher.processMessage(new BuildingTargetProgressMessage(targets, event));
  }

  private static void clearOutputFiles(CompileContext context,
                                       SourceToOutputMapping mapping,
                                       BuildTargetType<?> targetType,
                                       int targetId) throws IOException {
    Set<Path> dirsToDelete = targetType instanceof ModuleBasedBuildTargetType<?> ? FileCollectionFactory.createCanonicalPathSet() : null;
    OutputToTargetMapping outputToTargetRegistry = context.getProjectDescriptor().dataManager.getOutputToTargetMapping();
    for (SourceToOutputMappingCursor cursor = mapping.cursor(); cursor.hasNext(); ) {
      cursor.next();
      String [] outs = cursor.getOutputPaths();
      if (outs.length > 0) {
        List<String> deletedPaths = new ArrayList<>();
        for (String out : outs) {
          BuildOperations.deleteRecursivelyAndCollectDeleted(Path.of(out), deletedPaths, dirsToDelete);
        }
        outputToTargetRegistry.removeMappings(Arrays.asList(outs), targetId, mapping);
        if (!deletedPaths.isEmpty()) {
          context.processMessage(new FileDeletedEvent(deletedPaths));
        }
      }
    }
    if (dirsToDelete != null) {
      FSOperations.pruneEmptyDirs(context, dirsToDelete);
    }
  }

  private static void notifyChunkRebuildRequested(CompileContext context, ModuleChunk chunk, ModuleLevelBuilder builder) {
    String infoMessage = JpsBuildBundle.message("builder.0.requested.rebuild.of.module.chunk.1", builder.getPresentableName(), chunk.getName());
    LOG.info(infoMessage);
    BuildMessage.Kind kind = BuildMessage.Kind.JPS_INFO;
    final CompileScope scope = context.getScope();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (!scope.isWholeTargetAffected(target)) {
        infoMessage += ".\n";
        infoMessage += JpsBuildBundle.message("build.message.consider.building.whole.project.or.rebuilding.the.module");
        kind = BuildMessage.Kind.INFO;
        break;
      }
    }
    context.processMessage(new CompilerMessage("", kind, infoMessage));
  }

  private void storeBuilderStatistics(Builder builder, long elapsedTime, int processedFiles) {
    myElapsedTimeNanosByBuilder.computeIfAbsent(builder, b -> new AtomicLong()).addAndGet(elapsedTime);
    myNumberOfSourcesProcessedByBuilder.computeIfAbsent(builder, b -> new AtomicInteger()).addAndGet(processedFiles);
  }

  private static void saveInstrumentedClasses(@NotNull ChunkBuildOutputConsumerImpl outputConsumer) throws IOException {
    for (CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
      if (compiledClass.isDirty()) {
        compiledClass.save();
      }
    }
  }

  private static @NotNull CompileContext createContextWrapper(@NotNull CompileContext delegate) {
    UserDataHolderBase localDataHolder = new UserDataHolderBase();
    Set<Object> deletedKeysSet = ConcurrentHashMap.newKeySet();
    Class<UserDataHolder> dataHolderInterface = UserDataHolder.class;
    Class<MessageHandler> messageHandlerInterface = MessageHandler.class;
    return (CompileContext)Proxy.newProxyInstance(
      delegate.getClass().getClassLoader(),
      new Class[]{CompileContext.class},
      (proxy, method, args) -> {
        if (args == null) {
          return ourLookup.unreflect(method).invoke(delegate);
        }

        final Class<?> declaringClass = method.getDeclaringClass();
        if (dataHolderInterface.equals(declaringClass)) {
          final Object firstArgument = args[0];
          if (!(firstArgument instanceof GlobalContextKey)) {
            final boolean isWriteOperation =
              args.length == 2 /*&& void.class.equals(method.getReturnType())*/;
            if (isWriteOperation) {
              if (args[1] == null) {
                deletedKeysSet.add(firstArgument);
              }
              else {
                deletedKeysSet.remove(firstArgument);
              }
            }
            else {
              if (deletedKeysSet.contains(firstArgument)) {
                return null;
              }
            }
            final Object result = method.invoke(localDataHolder, args);
            if (isWriteOperation || result != null) {
              return result;
            }
          }
        }
        else if (messageHandlerInterface.equals(declaringClass)) {
          final BuildMessage msg = (BuildMessage)args[0];
          if (msg.getKind() == BuildMessage.Kind.ERROR) {
            Utils.ERRORS_DETECTED_KEY.set(localDataHolder, Boolean.TRUE);
          }
        }
        return ourLookup.unreflect(method).bindTo(delegate).asSpreader(Object[].class, args.length)
          .invoke(args);
      });
  }
}
