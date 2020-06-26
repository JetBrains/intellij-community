// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.MappingFailedException;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.TimingLog;
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
import org.jetbrains.jps.javac.JavacMain;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.SharedThreadPool;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * @author Eugene Zhuravlev
 */
public final class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance(IncProjectBuilder.class);

  private static final String CLASSPATH_INDEX_FILE_NAME = "classpath.index";
  //private static final boolean GENERATE_CLASSPATH_INDEX = Boolean.parseBoolean(System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION, "false"));
  private static final boolean SYNC_DELETE = Boolean.parseBoolean(System.getProperty("jps.sync.delete", "false"));
  private static final GlobalContextKey<Set<BuildTarget<?>>> TARGET_WITH_CLEARED_OUTPUT = GlobalContextKey.create("_targets_with_cleared_output_");
  public static final int MAX_BUILDER_THREADS;
  static {
    int maxThreads = Math.min(6, Runtime.getRuntime().availableProcessors() - 1);
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
  private final List<MessageHandler> myMessageHandlers = new ArrayList<>();
  private final MessageHandler myMessageDispatcher = new MessageHandler() {
    @Override
    public void processMessage(BuildMessage msg) {
      for (MessageHandler h : myMessageHandlers) {
        h.processMessage(msg);
      }
    }
  };
  private final boolean myIsTestMode;

  private final int myTotalModuleLevelBuilderCount;
  private final List<Future> myAsyncTasks = Collections.synchronizedList(new ArrayList<>());
  private final ConcurrentMap<Builder, AtomicLong> myElapsedTimeNanosByBuilder = new ConcurrentHashMap<>();
  private final ConcurrentMap<Builder, AtomicInteger> myNumberOfSourcesProcessedByBuilder = new ConcurrentHashMap<>();

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry, Map<String, String> builderParams, CanceledStatus cs, final boolean isTestMode) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = cs;
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
    myIsTestMode = isTestMode;
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void checkUpToDate(CompileScope scope) {
    CompileContextImpl context = null;
    try {
      context = createContext(scope);
      final BuildFSState fsState = myProjectDescriptor.fsState;
      for (BuildTarget<?> target : myProjectDescriptor.getBuildTargetIndex().getAllTargets()) {
        if (scope.isAffected(target)) {
          BuildOperations.ensureFSStateInitialized(context, target, true);
          final FilesDelta delta = fsState.getEffectiveFilesDelta(context, target);
          delta.lockData();
          try {
            for (Set<File> files : delta.getSourcesToRecompile().values()) {
              for (File file : files) {
                if (scope.isAffected(target, file)) {
                  // this will serve as a marker that compiler has work to do
                  myMessageDispatcher.processMessage(DoneSomethingNotification.INSTANCE);
                  return;
                }
              }
            }
          }
          finally {
            delta.unlockData();
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
      // this will serve as a marker that compiler has work to do
      myMessageDispatcher.processMessage(DoneSomethingNotification.INSTANCE);
    }
    finally {
      if (context != null) {
        flushContext(context);
      }
    }
  }


  public void build(CompileScope scope, boolean forceCleanCaches) throws RebuildRequestedException {
    checkRebuildRequired(scope);

    final LowMemoryWatcher memWatcher = LowMemoryWatcher.register(() -> {
      JavacMain.clearCompilerZipFileCache();
      myProjectDescriptor.dataManager.flush(false);
      myProjectDescriptor.getProjectStamps().getStampStorage().force();
    });

    final CleanupTempDirectoryExtension cleaner = CleanupTempDirectoryExtension.getInstance();
    final Future<Void> cleanupTask = cleaner != null && cleaner.getCleanupTask() != null? cleaner.getCleanupTask() : startTempDirectoryCleanupTask(myProjectDescriptor);
    if (cleanupTask != null) {
      myAsyncTasks.add(cleanupTask);
    }

    CompileContextImpl context = null;
    BuildTargetSourcesState sourcesState = null;
    try {
      context = createContext(scope);
      sourcesState = new BuildTargetSourcesState(context);
      // Clear source state report if force clean or rebuild
      if (forceCleanCaches || context.isProjectRebuild()) {
        sourcesState.clearSourcesState();
      }
      runBuild(context, forceCleanCaches);
      myProjectDescriptor.dataManager.saveVersion();
      myProjectDescriptor.dataManager.reportUnhandledRelativizerPaths();
      sourcesState.reportSourcesState();
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
    }
    catch (StopBuildException e) {
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
      // If build was canceled for some reasons e.g compilation error we should report built modules
      if (sourcesState != null) sourcesState.reportSourcesState();
      // some builder decided to stop the build
      // report optional progress message if any
      final String msg = e.getMessage();
      if (!StringUtil.isEmptyOrSpaces(msg)) {
        myMessageDispatcher.processMessage(new ProgressMessage(msg));
      }
    }
    catch (BuildDataCorruptedException e) {
      LOG.info(e);
      requestRebuild(e, e);
    }
    catch (ProjectBuildException e) {
      LOG.info(e);
      final Throwable cause = e.getCause();
      if (cause instanceof MappingFailedException ||
          cause instanceof IOException ||
          cause instanceof BuildDataCorruptedException ||
          (cause instanceof RuntimeException && cause.getCause() instanceof IOException)) {
        requestRebuild(e, cause);
      }
      else {
        // should stop the build with error
        final String errMessage = e.getMessage();
        final CompilerMessage msg;
        if (StringUtil.isEmptyOrSpaces(errMessage)) {
          msg = new CompilerMessage("", cause != null ? cause : e);
        }
        else {
          final String causeMessage = cause != null ? cause.getMessage() : "";
          msg = new CompilerMessage("", BuildMessage.Kind.ERROR, StringUtil.isEmptyOrSpaces(causeMessage) || errMessage.trim().endsWith(causeMessage)
                                                                 ? errMessage
                                                                 : errMessage + ": " + causeMessage);
        }
        myMessageDispatcher.processMessage(msg);
      }
    }
    finally {
      memWatcher.stop();
      flushContext(context);
      // wait for async tasks
      final CanceledStatus status = context == null ? CanceledStatus.NULL : context.getCancelStatus();
      synchronized (myAsyncTasks) {
        for (Future task : myAsyncTasks) {
          if (status.isCanceled()) {
            break;
          }
          waitForTask(status, task);
        }
      }
    }
  }

  private void checkRebuildRequired(final CompileScope scope) throws RebuildRequestedException {
    if (myIsTestMode) {
      // do not use the heuristic in tests in order to properly test all cases
      return;
    }
    final BuildTargetsState targetsState = myProjectDescriptor.getTargetsState();
    final long timeThreshold = targetsState.getLastSuccessfulRebuildDuration() * 95 / 100; // 95% of last registered clean rebuild time
    if (timeThreshold <= 0) {
      return; // no stats available
    }
    // check that this is a whole-project incremental build
    // checking only JavaModuleBuildTargetType because these target types directly correspond to project modules
    for (BuildTargetType<?> type : JavaModuleBuildTargetType.ALL_TYPES) {
      if (!scope.isBuildIncrementally(type) || !scope.isAllTargetsOfTypeAffected(type)) {
        return;
      }
    }
    // compute estimated times for dirty targets
    long estimatedWorkTime = 0L;

    final Predicate<BuildTarget<?>> isAffected = new Predicate<BuildTarget<?>>() {
      private final Set<BuildTargetType<?>> allTargetsAffected = new HashSet<>(JavaModuleBuildTargetType.ALL_TYPES);
      @Override
      public boolean test(BuildTarget<?> target) {
        // optimization, since we know here that all targets of types JavaModuleBuildTargetType are affected
        return allTargetsAffected.contains(target.getTargetType()) || scope.isAffected(target);
      }
    };
    final BuildTargetIndex targetIndex = myProjectDescriptor.getBuildTargetIndex();
    for (BuildTarget<?> target : targetIndex.getAllTargets()) {
      if (!targetIndex.isDummy(target)) {
        final long avgTimeToBuild = targetsState.getAverageBuildTime(target.getTargetType());
        if (avgTimeToBuild > 0) {
          // 1. in general case this time should include dependency analysis and cache update times
          // 2. need to check isAffected() since some targets (like artifacts) may be unaffected even for rebuild
          if (targetsState.getTargetConfiguration(target).isTargetDirty(myProjectDescriptor) && isAffected.test(target)) {
            estimatedWorkTime += avgTimeToBuild;
          }
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Rebuild heuristic: estimated build time / timeThreshold : " + estimatedWorkTime + " / " + timeThreshold);
    }

    if (estimatedWorkTime >= timeThreshold) {
      final String message = "Too many modules require recompilation, forcing full project rebuild";
      LOG.info(message);
      LOG.info("Estimated build duration (linear): " + StringUtil.formatDuration(estimatedWorkTime));
      LOG.info("Last successful rebuild duration (linear): " + StringUtil.formatDuration(targetsState.getLastSuccessfulRebuildDuration()));
      LOG.info("Rebuild heuristic time threshold: " + StringUtil.formatDuration(timeThreshold));
      myMessageDispatcher.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, message));
      throw new RebuildRequestedException(null);
    }
  }

  private void requestRebuild(Exception e, Throwable cause) throws RebuildRequestedException {
    myMessageDispatcher.processMessage(new CompilerMessage(
      "", BuildMessage.Kind.INFO, "Internal caches are corrupted or have outdated format, forcing project rebuild: " + e.getMessage())
    );
    throw new RebuildRequestedException(cause);
  }

  private static void waitForTask(@NotNull CanceledStatus status, Future task) {
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
    final StringBuilder message = new StringBuilder();
    if (modules.size() > 1) {
      message.append("Modules ");
      final int namesLimit = 5;
      int idx = 0;
      for (Iterator<JpsModule> iterator = modules.iterator(); iterator.hasNext(); ) {
        final JpsModule module = iterator.next();
        if (idx == namesLimit && iterator.hasNext()) {
          message.append(" and ").append(modules.size() - namesLimit).append(" others");
          break;
        }
        if (idx > 0) {
          message.append(", ");
        }
        message.append("\"").append(module.getName()).append("\"");
        idx += 1;
      }
      message.append(" were");
    }
    else {
      message.append("Module \"").append(modules.iterator().next().getName()).append("\" was");
    }
    message.append(" fully rebuilt due to project configuration");
    if (ModuleBuildTarget.REBUILD_ON_DEPENDENCY_CHANGE) {
      message.append("/dependencies");
    }
    message.append(" changes");
    context.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, message.toString()));
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
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.getProjectStamps().getStampStorage().force();
      pd.dataManager.flush(false);
    }
    final ExternalJavacManager server = ExternalJavacManager.KEY.get(context);
    if (server != null) {
      server.stop();
      ExternalJavacManager.KEY.set(context, null);
    }
  }

  private static boolean isParallelBuild(CompileContext context) {
    return Boolean.parseBoolean(context.getBuilderParameter(BuildParametersKeys.IS_AUTOMAKE)) ?
      BuildRunner.PARALLEL_BUILD_AUTOMAKE_ENABLED : BuildRunner.PARALLEL_BUILD_ENABLED;
  }

  private void runBuild(final CompileContextImpl context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info("Building project; isRebuild:" +
             context.isProjectRebuild() +
             "; isMake:" +
             context.isMake() +
             " parallel compilation:" +
             isParallelBuild(context));

    context.addBuildListener(new ChainedTargetsBuildListener(context));

    //Deletes class loader classpath index files for changed output roots
    context.addBuildListener(new BuildListener() {
      @Override
      public void filesGenerated(@NotNull FileGeneratedEvent event) {
        final Set<File> outputs = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
        for (Pair<String, String> pair : event.getPaths()) {
          outputs.add(new File(pair.getFirst()));
        }
        for (File root : outputs) {
          //noinspection ResultOfMethodCallIgnored
          new File(root, CLASSPATH_INDEX_FILE_NAME).delete();
        }
      }
    });

    for (TargetBuilder builder : myBuilderRegistry.getTargetBuilders()) {
      builder.buildStarted(context);
    }
    for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
      builder.buildStarted(context);
    }

    BuildProgress buildProgress = null;
    try {
      buildProgress = new BuildProgress(myProjectDescriptor.dataManager, myProjectDescriptor.getBuildTargetIndex(),
                                        myProjectDescriptor.getBuildTargetIndex().getSortedTargetChunks(context),
                                        chunk -> isAffected(context.getScope(), chunk));

      // clean roots for targets for which rebuild is forced
      cleanOutputRoots(context, context.isProjectRebuild() || forceCleanCaches);

      context.processMessage(new ProgressMessage("Running 'before' tasks"));
      runTasks(context, myBuilderRegistry.getBeforeTasks());
      TimingLog.LOG.debug("'before' tasks finished");

      context.processMessage(new ProgressMessage("Checking sources"));
      buildChunks(context, buildProgress);
      TimingLog.LOG.debug("Building targets finished");

      context.processMessage(new ProgressMessage("Running 'after' tasks"));
      runTasks(context, myBuilderRegistry.getAfterTasks());
      TimingLog.LOG.debug("'after' tasks finished");
      sendElapsedTimeMessages(context);
    }
    finally {
      if (buildProgress != null) {
        buildProgress.updateExpectedAverageTime();
        if (context.isProjectRebuild() && !Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
          myProjectDescriptor.getTargetsState().setLastSuccessfulRebuildDuration(buildProgress.getAbsoluteBuildTime());
        }
      }
      for (TargetBuilder builder : myBuilderRegistry.getTargetBuilders()) {
        builder.buildFinished(context);
      }
      for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
        builder.buildFinished(context);
      }
      context.processMessage(new ProgressMessage("Finished, saving caches..."));
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

  static @Nullable Future<Void> startTempDirectoryCleanupTask(final ProjectDescriptor pd) {
    final String tempPath = System.getProperty("java.io.tmpdir", null);
    if (StringUtil.isEmptyOrSpaces(tempPath)) {
      return null;
    }
    final File tempDir = new File(tempPath);
    final File dataRoot = pd.dataManager.getDataPaths().getDataStorageRoot();
    if (!FileUtil.isAncestor(dataRoot, tempDir, true)) {
      // cleanup only 'local' temp
      return null;
    }
    final File[] files = tempDir.listFiles();
    if (files == null) {
      tempDir.mkdirs(); // ensure the directory exists
    }
    else if (files.length > 0) {
      final RunnableFuture<Void> task = new FutureTask<>(() -> {
        for (File tempFile : files) {
          FileUtil.delete(tempFile);
        }
      }, null);
      final Thread thread = new Thread(task, "Temp directory cleanup");
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setDaemon(true);
      thread.start();
      return task;
    }
    return null;
  }

  private CompileContextImpl createContext(CompileScope scope) {
    return new CompileContextImpl(scope, myProjectDescriptor, myMessageDispatcher, myBuilderParams, myCancelStatus);
  }

  private void cleanOutputRoots(CompileContext context, boolean cleanCaches) throws ProjectBuildException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    ProjectBuildException ex = null;
    try {
      final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(projectDescriptor.getProject());
      final boolean shouldClear = configuration.isClearOutputDirectoryOnRebuild();
      if (shouldClear) {
        clearOutputs(context);
      }
      else {
        for (BuildTarget<?> target : projectDescriptor.getBuildTargetIndex().getAllTargets()) {
          context.checkCanceled();
          if (context.getScope().isBuildForced(target)) {
            clearOutputFilesUninterruptibly(context, target);
          }
        }
      }
      for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
        if (context.getScope().isAllTargetsOfTypeAffected(type)) {
          cleanOutputOfStaleTargets(type, context);
        }
      }
    }
    catch (ProjectBuildException e) {
      ex = e;
    }
    finally {
      if (cleanCaches) {
        try {
          projectDescriptor.getProjectStamps().getStampStorage().clean();
        }
        catch (IOException e) {
          if (ex == null) {
            ex = new ProjectBuildException("Error cleaning timestamps storage", e);
          }
          else {
            LOG.info("Error cleaning timestamps storage", e);
          }
        }
        finally {
          try {
            projectDescriptor.dataManager.clean();
          }
          catch (IOException e) {
            if (ex == null) {
              ex = new ProjectBuildException("Error cleaning compiler storages", e);
            }
            else {
              LOG.info("Error cleaning compiler storages", e);
            }
          }
          finally {
            projectDescriptor.fsState.clearAll();
            if (ex != null) {
              throw ex;
            }
          }
        }
      }
      else {
        final BuildTargetsState targetsState = projectDescriptor.getTargetsState();
        for (BuildTarget<?> target : getTargetsWithClearedOutput(context)) {
          // This will ensure the target will be fully rebuilt either in this or in the future build session.
          // if this build fails or is cancelled, all such targets will still be marked as needing recompilation
          targetsState.getTargetConfiguration(target).invalidate();
        }
      }
    }
  }

  private void cleanOutputOfStaleTargets(BuildTargetType<?> type, CompileContext context) {
    List<Pair<String, Integer>> targetIds = myProjectDescriptor.dataManager.getTargetsState().getStaleTargetIds(type);
    if (targetIds.isEmpty()) return;

    context.processMessage(new ProgressMessage("Cleaning old output directories..."));
    for (Pair<String, Integer> ids : targetIds) {
      String stringId = ids.first;
      try {
        SourceToOutputMappingImpl mapping = null;
        try {
          mapping = myProjectDescriptor.dataManager.createSourceToOutputMapForStaleTarget(type, stringId);
          clearOutputFiles(context, mapping, type, ids.second);
        }
        finally {
          if (mapping != null) {
            mapping.close();
          }
        }
        FileUtil.delete(myProjectDescriptor.dataManager.getDataPaths().getTargetDataRoot(type, stringId));
        myProjectDescriptor.dataManager.getTargetsState().cleanStaleTarget(type, stringId);
      }
      catch (IOException e) {
        LOG.warn(e);
        myMessageDispatcher.processMessage(new CompilerMessage("", BuildMessage.Kind.WARNING, "Failed to delete output files from obsolete '" + stringId + "' target: " + e.toString()));
      }
    }
  }

  public static void clearOutputFiles(CompileContext context, BuildTarget<?> target) throws IOException {
    final SourceToOutputMapping map = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
    BuildTargetType<?> targetType = target.getTargetType();
    clearOutputFiles(context, map, targetType, context.getProjectDescriptor().dataManager.getTargetsState().getBuildTargetId(target));
    registerTargetsWithClearedOutput(context, Collections.singletonList(target));
  }

  private static void clearOutputFiles(CompileContext context,
                                       SourceToOutputMapping mapping,
                                       BuildTargetType<?> targetType,
                                       int targetId) throws IOException {
    final THashSet<File> dirsToDelete = targetType instanceof ModuleBasedBuildTargetType<?>
                                        ? new THashSet<>(FileUtil.FILE_HASHING_STRATEGY) : null;
    OutputToTargetRegistry outputToTargetRegistry = context.getProjectDescriptor().dataManager.getOutputToTargetRegistry();
    for (String srcPath : mapping.getSources()) {
      final Collection<String> outs = mapping.getOutputs(srcPath);
      if (outs != null && !outs.isEmpty()) {
        List<String> deletedPaths = new ArrayList<>();
        for (String out : outs) {
          BuildOperations.deleteRecursively(out, deletedPaths, dirsToDelete);
        }
        outputToTargetRegistry.removeMapping(outs, targetId);
        if (!deletedPaths.isEmpty()) {
          context.processMessage(new FileDeletedEvent(deletedPaths));
        }
      }
    }
    if (dirsToDelete != null) {
      FSOperations.pruneEmptyDirs(context, dirsToDelete);
    }
  }

  private static void registerTargetsWithClearedOutput(CompileContext context, Collection<? extends BuildTarget<?>> targets) {
    synchronized (TARGET_WITH_CLEARED_OUTPUT) {
      Set<BuildTarget<?>> data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT);
      if (data == null) {
        data = new THashSet<>();
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

  private static Set<BuildTarget<?>> getTargetsWithClearedOutput(CompileContext context) {
    synchronized (TARGET_WITH_CLEARED_OUTPUT) {
      Set<BuildTarget<?>> data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT);
      return data != null? Collections.unmodifiableSet(new HashSet<>(data)) : Collections.emptySet();
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

  private void clearOutputs(CompileContext context) throws ProjectBuildException {
    final long cleanStart = System.nanoTime();
    final MultiMap<File, BuildTarget<?>> rootsToDelete = MultiMap.createSet();
    final Set<File> allSourceRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);

    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    final List<? extends BuildTarget<?>> allTargets = projectDescriptor.getBuildTargetIndex().getAllTargets();
    for (BuildTarget<?> target : allTargets) {
      if (target instanceof ModuleBasedTarget) {
        for (File file : target.getOutputRoots(context)) {
          rootsToDelete.putValue(file, target);
        }
      }
      else {
        if (context.getScope().isBuildForced(target)) {
          clearOutputFilesUninterruptibly(context, target);
        }
      }
    }

    final ModuleExcludeIndex moduleIndex = projectDescriptor.getModuleExcludeIndex();
    for (BuildTarget<?> target : allTargets) {
      for (BuildRootDescriptor descriptor : projectDescriptor.getBuildRootIndex().getTargetRoots(target, context)) {
        // excluding from checks roots with generated sources; because it is safe to delete generated stuff
        if (!descriptor.isGenerated()) {
          File rootFile = descriptor.getRootFile();
          //some roots aren't marked by as generated but in fact they are produced by some builder and it's safe to remove them.
          //However if a root isn't excluded it means that its content will be shown in 'Project View' and an user can create new files under it so it would be dangerous to clean such roots
          if (moduleIndex.isInContent(rootFile)) {
            allSourceRoots.add(rootFile);
          }
        }
      }
    }

    // check that output and source roots are not overlapping
    final CompileScope compileScope = context.getScope();
    final List<File> filesToDelete = new ArrayList<>();
    final Predicate<BuildTarget<?>> forcedBuild = input -> compileScope.isBuildForced(input);
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
          final Set<File> _outRoot = new THashSet<>(Arrays.asList(outputRoot), FileUtil.FILE_HASHING_STRATEGY);
          for (File srcRoot : allSourceRoots) {
            if (JpsPathUtil.isUnder(_outRoot, srcRoot)) {
              okToDelete = false;
              break;
            }
          }
        }
        if (!okToDelete) {
          context.processMessage(new CompilerMessage(
            "", BuildMessage.Kind.WARNING, "Output path " + outputRoot.getPath() + " intersects with a source root. Only files that were created by build will be cleaned.")
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
        else { // the output root must be file
          if (!outputRoot.delete()) {
            filesToDelete.add(outputRoot);
          }
        }
        registerTargetsWithClearedOutput(context, rootTargets);
      }
      else {
        context.processMessage(new ProgressMessage("Cleaning output directories..."));
        // clean only those files we are aware of
        for (BuildTarget<?> target : rootTargets) {
          if (compileScope.isBuildForced(target)) {
            clearOutputFilesUninterruptibly(context, target);
          }
        }
      }
    }

    if (!filesToDelete.isEmpty()) {
      context.processMessage(new ProgressMessage("Cleaning output directories..."));
      if (SYNC_DELETE) {
        for (File file : filesToDelete) {
          context.checkCanceled();
          FileUtil.delete(file);
        }
      }
      else {
        myAsyncTasks.add(FileUtil.asyncDelete(filesToDelete));
      }
    }
    LOG.info("Cleaned output directories in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cleanStart) + " ms");
  }

  private static boolean isEmpty(File outputRoot) {
    final String[] files = outputRoot.list();
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
      context.processMessage(new CompilerMessage("", BuildMessage.Kind.WARNING, "Problems clearing output files for target \"" + target.getPresentableName() + "\": " + reason));
    }
  }

  private static void runTasks(CompileContext context, final List<? extends BuildTask> tasks) throws ProjectBuildException {
    for (BuildTask task : tasks) {
      task.build(context);
    }
  }

  private void buildChunks(final CompileContextImpl context, BuildProgress buildProgress) throws ProjectBuildException {
    try {

      boolean compileInParallel = isParallelBuild(context);
      if (compileInParallel && MAX_BUILDER_THREADS <= 1) {
        LOG.info("Switched off parallel compilation because maximum number of builder threads is less than 2. Set '"
                 + GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION + "' system property to a value greater than 1 to really enable parallel compilation.");
        compileInParallel = false;
      }

      if (compileInParallel) {
        new BuildParallelizer(context, buildProgress).buildInParallel();
      }
      else {
        // non-parallel build
        ProjectDescriptor pd = context.getProjectDescriptor();
        for (BuildTargetChunk chunk : pd.getBuildTargetIndex().getSortedTargetChunks(context)) {
          try {
            buildChunkIfAffected(context, context.getScope(), chunk, buildProgress);
          }
          finally {
            pd.dataManager.closeSourceToOutputStorages(Collections.singleton(chunk));
            pd.dataManager.flush(true);
          }
        }
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static final class BuildChunkTask {
    private final BuildTargetChunk myChunk;
    private final Set<BuildChunkTask> myNotBuiltDependencies = new THashSet<>();
    private final List<BuildChunkTask> myTasksDependsOnThis = new ArrayList<>();
    private int mySelfScore = 0;
    private int myDepsScore = 0;

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
      return myNotBuiltDependencies.isEmpty();
    }

    public void addDependency(BuildChunkTask dependency) {
      if (myNotBuiltDependencies.add(dependency)) {
        dependency.myTasksDependsOnThis.add(this);
      }
    }

    public List<BuildChunkTask> markAsFinishedAndGetNextReadyTasks() {
      List<BuildChunkTask> nextTasks = new SmartList<>();
      for (BuildChunkTask task : myTasksDependsOnThis) {
        final boolean removed = task.myNotBuiltDependencies.remove(this);
        LOG.assertTrue(removed, task.getChunk().toString() + " didn't have " + getChunk().toString());

        if (task.isReady()) {
          nextTasks.add(task);
        }
      }
      return nextTasks;
    }
  }

  private final class BuildParallelizer {
    private final ExecutorService myParallelBuildExecutor = AppExecutorUtil.createCustomPriorityQueueBoundedApplicationPoolExecutor(
      "IncProjectBuilder Executor Pool", SharedThreadPool.getInstance(), MAX_BUILDER_THREADS, (o1, o2) -> {
      int p1 = o1 instanceof RunnableWithPriority ? ((RunnableWithPriority)o1).priority : 1;
      int p2 = o1 instanceof RunnableWithPriority ? ((RunnableWithPriority)o2).priority : 1;
      return Integer.compare(p2, p1);
    });
    private final CompileContext myContext;
    private final BuildProgress myBuildProgress;
    private final AtomicReference<Throwable> myException = new AtomicReference<>();
    private final Object myQueueLock = new Object();
    private final CountDownLatch myTasksCountDown;
    private final List<BuildChunkTask> myTasks;

    private BuildParallelizer(CompileContext context, BuildProgress buildProgress) {
      myContext = context;
      myBuildProgress = buildProgress;
      final ProjectDescriptor pd = myContext.getProjectDescriptor();
      final BuildTargetIndex targetIndex = pd.getBuildTargetIndex();

      List<BuildTargetChunk> chunks = targetIndex.getSortedTargetChunks(myContext);
      myTasks = new ArrayList<>(chunks.size());
      Map<BuildTarget<?>, BuildChunkTask> targetToTask = new THashMap<>(chunks.size());
      for (BuildTargetChunk chunk : chunks) {
        BuildChunkTask task = new BuildChunkTask(chunk);
        myTasks.add(task);
        for (BuildTarget<?> target : chunk.getTargets()) {
          targetToTask.put(target, task);
          task.mySelfScore += 1;
        }
      }

      Map<BuildTarget<?>, Collection<BuildTarget<?>>> transitiveDependencyCache = new HashMap<>(myTasks.size());

      for (BuildChunkTask task : myTasks) {
        for (BuildTarget<?> target : task.getChunk().getTargets()) {
          for (BuildTarget<?> dependency : targetIndex.getDependencies(target, myContext)) {
            BuildChunkTask depTask = targetToTask.get(dependency);
            if (depTask != null && depTask != task) {
              task.addDependency(depTask);
            }
          }
          for (BuildTarget<?> dependency : getTransitiveDeps(targetIndex, target, myContext, transitiveDependencyCache)) {
            BuildChunkTask depTask = targetToTask.get(dependency);
            if (depTask != null && depTask != task) {
              depTask.myDepsScore += task.mySelfScore;
            }
          }
        }
      }

      myTasksCountDown = new CountDownLatch(myTasks.size());
    }

    public void buildInParallel() throws IOException, ProjectBuildException {
      List<BuildChunkTask> initialTasks = new ArrayList<>();
      for (BuildChunkTask task : myTasks) {
        if (task.isReady()) {
          initialTasks.add(task);
        }
      }
      queueTasks(initialTasks);

      try {
        myTasksCountDown.await();
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

    private void queueTasks(List<? extends BuildChunkTask> tasks) {
      if (tasks.isEmpty()) return;
      ArrayList<? extends BuildChunkTask> sorted = new ArrayList<>(tasks);
      sorted.sort(Comparator.comparingLong(BuildChunkTask::getScore).reversed());

      if (LOG.isDebugEnabled()) {
        final List<BuildTargetChunk> chunksToLog = new ArrayList<>();
        for (BuildChunkTask task : sorted) {
          chunksToLog.add(task.getChunk());
        }
        final StringBuilder logBuilder = new StringBuilder("Queuing " + chunksToLog.size() + " chunks in parallel: ");
        chunksToLog.sort(Comparator.comparing(BuildTargetChunk::toString));
        for (BuildTargetChunk chunk : chunksToLog) {
          logBuilder.append(chunk.toString()).append("; ");
        }
        LOG.debug(logBuilder.toString());
      }
      for (BuildChunkTask task : sorted) {
        queueTask(task);
      }
    }

    private abstract class RunnableWithPriority implements Runnable {
      public final int priority;

      RunnableWithPriority(int priority) {
        this.priority = priority;
      }
    }

    private void queueTask(final BuildChunkTask task) {
      final CompileContext chunkLocalContext = createContextWrapper(myContext);
      myParallelBuildExecutor.execute(new RunnableWithPriority(task.getScore()) {
        @Override
        public void run() {
          try {
            try {
              if (myException.get() == null) {
                buildChunkIfAffected(chunkLocalContext, myContext.getScope(), task.getChunk(), myBuildProgress);
              }
            }
            finally {
              myProjectDescriptor.dataManager.closeSourceToOutputStorages(Collections.singletonList(task.getChunk()));
              myProjectDescriptor.dataManager.flush(true);
            }
          }
          catch (Throwable e) {
            myException.compareAndSet(null, e);
            LOG.info(e);
          }
          finally {
            LOG.debug("Finished compilation of " + task.getChunk().toString());
            myTasksCountDown.countDown();
            List<BuildChunkTask> nextTasks;
            synchronized (myQueueLock) {
              nextTasks = task.markAsFinishedAndGetNextReadyTasks();
            }
            if (!nextTasks.isEmpty()) {
              queueTasks(nextTasks);
            }
          }
        }
      });
    }
  }

  private static Iterable<? extends BuildTarget<?>> getTransitiveDeps(BuildTargetIndex index,
                                                                      BuildTarget<?> target,
                                                                      CompileContext context,
                                                                      Map<BuildTarget<?>, Collection<BuildTarget<?>>> cache) {
    if (cache.containsKey(target)) {
      return cache.get(target);
    }
    Set<BuildTarget<?>> result = new HashSet<>();
    LinkedList<BuildTarget<?>> queue = new LinkedList<>();
    queue.add(target);
    result.add(target);
    while (!queue.isEmpty()) {
      BuildTarget next = queue.pop();
      Collection<BuildTarget<?>> transitive = cache.get(next);
      if (transitive != null) {
        result.addAll(transitive);
      }
      else {
        Collection<BuildTarget<?>> dependencies = index.getDependencies(next, context);
        for (BuildTarget<?> dependency : dependencies) {
          if (dependency != target && result.add(dependency)) queue.add(dependency);
        }
      }
    }
    result.remove(target);
    cache.put(target, result);
    return result;
  }

  private void buildChunkIfAffected(CompileContext context, CompileScope scope, BuildTargetChunk chunk,
                                    BuildProgress buildProgress) throws ProjectBuildException {
    if (isAffected(scope, chunk)) {
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

  private boolean runBuildersForChunk(final CompileContext context,
                                      final BuildTargetChunk chunk,
                                      BuildProgress buildProgress) throws ProjectBuildException, IOException {
    Set<? extends BuildTarget<?>> targets = chunk.getTargets();
    if (targets.size() > 1) {
      Set<ModuleBuildTarget> moduleTargets = new LinkedHashSet<>();
      for (BuildTarget<?> target : targets) {
        if (target instanceof ModuleBuildTarget) {
          moduleTargets.add((ModuleBuildTarget)target);
        }
        else {
          String targetsString = StringUtil.join(targets,
                                                 (Function<BuildTarget<?>, String>)target1 -> StringUtil.decapitalize(target1.getPresentableName()), ", ");
          context.processMessage(new CompilerMessage(
            "", BuildMessage.Kind.ERROR, "Cannot build " + StringUtil.decapitalize(target.getPresentableName()) + " because it is included into a circular dependency (" +
                                         targetsString + ")")
          );
          return false;
        }
      }

      return runModuleLevelBuilders(context, new ModuleChunk(moduleTargets), buildProgress);
    }

    final BuildTarget<?> target = targets.iterator().next();
    if (target instanceof ModuleBuildTarget) {
      return runModuleLevelBuilders(context, new ModuleChunk(Collections.singleton((ModuleBuildTarget)target)), buildProgress);
    }

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

  private <R extends BuildRootDescriptor, T extends BuildTarget<R>>
  void buildTarget(final T target, final CompileContext context, TargetBuilder<?, ?> builder) throws ProjectBuildException, IOException {

    if (builder.getTargetTypes().contains(target.getTargetType())) {
      DirtyFilesHolder<R, T> holder = new DirtyFilesHolderBase<R, T>(context) {
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

  private static <T extends BuildRootDescriptor>
  void cleanOldOutputs(final CompileContext context, final BuildTarget<T> target) throws ProjectBuildException, IOException {
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

      fsState.beforeChunkBuildStart(context, chunk);

      doneSomething |= runBuildersForChunk(context, chunk, buildProgress);

      fsState.clearContextRoundData(context);
      fsState.clearContextChunk(context);

      if (doneSomething) {
        BuildOperations.markTargetsUpToDate(context, chunk);
      }

      //if (doneSomething && GENERATE_CLASSPATH_INDEX) {
      //  myAsyncTasks.add(SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
      //    @Override
      //    public void run() {
      //      createClasspathIndex(chunk);
      //    }
      //  }));
      //}
    }
    catch (BuildDataCorruptedException | ProjectBuildException e) {
      throw e;
    }
    catch (Throwable e) {
      final StringBuilder message = new StringBuilder();
      message.append(chunk.getPresentableName()).append(": ").append(e.getClass().getName());
      final String exceptionMessage = e.getMessage();
      if (exceptionMessage != null) {
        message.append(": ").append(exceptionMessage);
      }
      throw new ProjectBuildException(message.toString(), e);
    }
    finally {
      buildProgress.onTargetChunkFinished(chunk, context);
      for (BuildRootDescriptor rd : context.getProjectDescriptor().getBuildRootIndex().clearTempRoots(context)) {
        context.getProjectDescriptor().fsState.clearRecompile(rd);
      }
      try {
        // restore deleted paths that were not processed by 'integrate'
        final Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
        if (map != null) {
          for (Map.Entry<BuildTarget<?>, Collection<String>> entry : map.entrySet()) {
            final BuildTarget<?> target = entry.getKey();
            final Collection<String> paths = entry.getValue();
            if (paths != null) {
              for (String path : paths) {
                fsState.registerDeleted(context, target, new File(path), null);
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
      }
    }
  }

  private void sendBuildingTargetMessages(@NotNull Set<? extends BuildTarget<?>> targets, @NotNull BuildingTargetProgressMessage.Event event) {
    myMessageDispatcher.processMessage(new BuildingTargetProgressMessage(targets, event));
  }

  //private static void createClasspathIndex(final BuildTargetChunk chunk) {
  //  final Set<File> outputDirs = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  //  for (BuildTarget<?> target : chunk.getTargets()) {
  //    if (target instanceof ModuleBuildTarget) {
  //      File outputDir = ((ModuleBuildTarget)target).getOutputDir();
  //      if (outputDir != null && outputDirs.add(outputDir)) {
  //        try {
  //          BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputDir, CLASSPATH_INDEX_FILE_NAME)));
  //          try {
  //            writeIndex(writer, outputDir, "");
  //          }
  //          finally {
  //            writer.close();
  //          }
  //        }
  //        catch (IOException e) {
  //          // Ignore. Failed to create optional classpath index
  //        }
  //      }
  //    }
  //  }
  //}

  //private static void writeIndex(final BufferedWriter writer, final File file, final String path) throws IOException {
  //  writer.write(path);
  //  writer.write('\n');
  //  final File[] files = file.listFiles();
  //  if (files != null) {
  //    for (File child : files) {
  //      final String _path = path.isEmpty() ? child.getName() : path + "/" + child.getName();
  //      writeIndex(writer, child, _path);
  //    }
  //  }
  //}


  private boolean processDeletedPaths(CompileContext context, final Set<? extends BuildTarget<?>> targets) throws ProjectBuildException {
    boolean doneSomething = false;
    try {
      // cleanup outputs
      final Map<BuildTarget<?>, Collection<String>> targetToRemovedSources = new HashMap<>();

      final THashSet<File> dirsToDelete = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      for (BuildTarget<?> target : targets) {

        final Collection<String> deletedPaths = myProjectDescriptor.fsState.getAndClearDeletedPaths(target);
        if (deletedPaths.isEmpty()) {
          continue;
        }
        targetToRemovedSources.put(target, deletedPaths);
        if (isTargetOutputCleared(context, target)) {
          continue;
        }
        final int buildTargetId = context.getProjectDescriptor().getTargetsState().getBuildTargetId(target);
        final boolean shouldPruneEmptyDirs = target instanceof ModuleBasedTarget;
        final SourceToOutputMapping sourceToOutputStorage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
        final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        // actually delete outputs associated with removed paths
        final Collection<String> pathsForIteration;
        if (myIsTestMode) {
          // ensure predictable order in test logs
          pathsForIteration = new ArrayList<>(deletedPaths);
          Collections.sort((List<String>)pathsForIteration);
        }
        else {
          pathsForIteration = deletedPaths;
        }
        for (String deletedSource : pathsForIteration) {
          // deleting outputs corresponding to non-existing source
          final Collection<String> outputs = sourceToOutputStorage.getOutputs(deletedSource);
          if (outputs != null && !outputs.isEmpty()) {
            List<String> deletedOutputPaths = new ArrayList<>();
            final OutputToTargetRegistry outputToSourceRegistry = context.getProjectDescriptor().dataManager.getOutputToTargetRegistry();
            for (String output : outputToSourceRegistry.getSafeToDeleteOutputs(outputs, buildTargetId)) {
              final boolean deleted = BuildOperations.deleteRecursively(output, deletedOutputPaths, shouldPruneEmptyDirs ? dirsToDelete : null);
              if (deleted) {
                doneSomething = true;
              }
            }
            for (String outputPath : outputs) {
              outputToSourceRegistry.removeMapping(outputPath, buildTargetId);
            }
            if (!deletedOutputPaths.isEmpty()) {
              if (logger.isEnabled()) {
                logger.logDeletedFiles(deletedOutputPaths);
              }
              context.processMessage(new FileDeletedEvent(deletedOutputPaths));
            }
          }

          if (target instanceof ModuleBuildTarget) {
            // check if deleted source was associated with a form
            final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
            final Collection<String> boundForms = sourceToFormMap.getState(deletedSource);
            if (boundForms != null) {
              for (String formPath : boundForms) {
                final File formFile = new File(formPath);
                if (formFile.exists()) {
                  FSOperations.markDirty(context, CompilationRound.CURRENT, formFile);
                }
              }
              sourceToFormMap.remove(deletedSource);
            }
          }
        }
      }
      if (!targetToRemovedSources.isEmpty()) {
        final Map<BuildTarget<?>, Collection<String>> existing = Utils.REMOVED_SOURCES_KEY.get(context);
        if (existing != null) {
          for (Map.Entry<BuildTarget<?>, Collection<String>> entry : existing.entrySet()) {
            final Collection<String> paths = targetToRemovedSources.get(entry.getKey());
            if (paths != null) {
              paths.addAll(entry.getValue());
            }
            else {
              targetToRemovedSources.put(entry.getKey(), entry.getValue());
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

  // return true if changed something, false otherwise
  private boolean runModuleLevelBuilders(final CompileContext context,
                                         final ModuleChunk chunk,
                                         BuildProgress buildProgress) throws ProjectBuildException, IOException {
    for (BuilderCategory category : BuilderCategory.values()) {
      for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
        builder.chunkBuildStarted(context, chunk);
      }
    }

    boolean doneSomething = false;
    boolean rebuildFromScratchRequested = false;
    boolean nextPassRequired;
    ChunkBuildOutputConsumerImpl outputConsumer = new ChunkBuildOutputConsumerImpl(context);
    try {
      do {
        nextPassRequired = false;
        myProjectDescriptor.fsState.beforeNextRoundStart(context, chunk);

        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder =
          new DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
            @Override
            public void processDirtyFiles(@NotNull FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget> processor)
              throws IOException {
              FSOperations.processFilesToRecompile(context, chunk, processor);
            }
          };
        if (!JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
          final Map<ModuleBuildTarget, Set<File>> cleanedSources = BuildOperations
            .cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder);
          for (Map.Entry<ModuleBuildTarget, Set<File>> entry : cleanedSources.entrySet()) {
            final ModuleBuildTarget target = entry.getKey();
            final Set<File> files = entry.getValue();
            if (!files.isEmpty()) {
              final SourceToOutputMapping mapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
              for (File srcFile : files) {
                mapping.setOutputs(srcFile.getPath(), Collections.emptyList());
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
                processDeletedPaths(context, chunk.getTargets());
                long start = System.nanoTime();
                int processedSourcesBefore = outputConsumer.getNumberOfProcessedSources();
                final ModuleLevelBuilder.ExitCode buildResult = builder.build(context, chunk, dirtyFilesHolder, outputConsumer);
                storeBuilderStatistics(builder, System.nanoTime() - start,
                                       outputConsumer.getNumberOfProcessedSources() - processedSourcesBefore);

                doneSomething |= (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE);

                if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
                  throw new StopBuildException("Builder " + builder.getPresentableName() + " requested build stop");
                }
                context.checkCanceled();
                if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
                  nextPassRequired = true;
                }
                else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
                  if (!rebuildFromScratchRequested && !JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
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
                  buildProgress.updateProgress(target, ((double)buildersPassed)/myTotalModuleLevelBuilderCount, context);
                }
              }
            }
            finally {
              final boolean moreToCompile = JavaBuilderUtil.updateMappingsOnRoundCompletion(context, dirtyFilesHolder, chunk);
              if (moreToCompile) {
                nextPassRequired = true;
              }
            }
          }
        }
        finally {
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
    }

    return doneSomething;
  }

  private static void notifyChunkRebuildRequested(CompileContext context, ModuleChunk chunk, ModuleLevelBuilder builder) {
    String infoMessage = "Builder \"" + builder.getPresentableName() + "\" requested rebuild of module chunk \"" + chunk.getName() + "\"";
    LOG.info(infoMessage);
    BuildMessage.Kind kind = BuildMessage.Kind.JPS_INFO;
    final CompileScope scope = context.getScope();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (!scope.isWholeTargetAffected(target)) {
        infoMessage += ".\nConsider building whole project or rebuilding the module.";
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

  private static void saveInstrumentedClasses(ChunkBuildOutputConsumerImpl outputConsumer) throws IOException {
    for (CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
      if (compiledClass.isDirty()) {
        compiledClass.save();
      }
    }
  }

  private static CompileContext createContextWrapper(final CompileContext delegate) {
    final ClassLoader loader = delegate.getClass().getClassLoader();
    final UserDataHolderBase localDataHolder = new UserDataHolderBase();
    final Set<Object> deletedKeysSet = ContainerUtil.newConcurrentSet();
    final Class<UserDataHolder> dataHolderInterface = UserDataHolder.class;
    final Class<MessageHandler> messageHandlerInterface = MessageHandler.class;
    return (CompileContext)Proxy.newProxyInstance(loader, new Class[]{CompileContext.class}, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();
        if (dataHolderInterface.equals(declaringClass)) {
          final Object firstArgument = args[0];
          if (!(firstArgument instanceof GlobalContextKey)) {
            final boolean isWriteOperation = args.length == 2 /*&& void.class.equals(method.getReturnType())*/;
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
        try {
          return method.invoke(delegate, args);
        }
        catch (InvocationTargetException e) {
          final Throwable targetEx = e.getTargetException();
          if (targetEx instanceof ProjectBuildException) {
            throw targetEx;
          }
          throw e;
        }
      }
    });
  }
}
