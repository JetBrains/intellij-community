// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Predicate;
import com.intellij.util.io.MappingFailedException;
import com.intellij.util.io.PersistentEnumerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.fs.FilesDelta;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration;
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.incremental.storage.OutputToTargetRegistry;
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

/**
 * @author Eugene Zhuravlev
 */
public class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.IncProjectBuilder");

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
  @Nullable private final Callbacks.ConstantAffectionResolver myConstantSearch;
  private final List<MessageHandler> myMessageHandlers = new ArrayList<>();
  private final MessageHandler myMessageDispatcher = new MessageHandler() {
    public void processMessage(BuildMessage msg) {
      for (MessageHandler h : myMessageHandlers) {
        h.processMessage(msg);
      }
    }
  };
  private final boolean myIsTestMode;

  private volatile float myTargetsProcessed = 0.0f;
  private volatile float myTotalTargetsWork;
  private final int myTotalModuleLevelBuilderCount;
  private final List<Future> myAsyncTasks = Collections.synchronizedList(new ArrayList<Future>());
  private final ConcurrentMap<Builder, AtomicLong> myElapsedTimeNanosByBuilder = ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<Builder, AtomicInteger> myNumberOfSourcesProcessedByBuilder = ContainerUtil.newConcurrentMap();

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry, Map<String, String> builderParams, CanceledStatus cs,
                           @Nullable Callbacks.ConstantAffectionResolver constantSearch, final boolean isTestMode) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = cs;
    myConstantSearch = constantSearch;
    myTotalTargetsWork = pd.getBuildTargetIndex().getAllTargets().size();
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
          BuildOperations.ensureFSStateInitialized(context, target);
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

    final LowMemoryWatcher memWatcher = LowMemoryWatcher.register(() -> {
      JavacMain.clearCompilerZipFileCache();
      myProjectDescriptor.dataManager.flush(false);
      myProjectDescriptor.timestamps.getStorage().force();
    });
    
    startTempDirectoryCleanupTask();
    
    CompileContextImpl context = null;
    try {
      context = createContext(scope);
      runBuild(context, forceCleanCaches);
      myProjectDescriptor.dataManager.saveVersion();
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
    }
    catch (StopBuildException e) {
      reportRebuiltModules(context);
      reportUnprocessedChanges(context);
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
      if (cause instanceof PersistentEnumerator.CorruptedException ||
          cause instanceof MappingFailedException ||
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

  private void requestRebuild(Exception e, Throwable cause) throws RebuildRequestedException {
    myMessageDispatcher.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO,
                                                           "Internal caches are corrupted or have outdated format, forcing project rebuild: " +
                                                           e.getMessage()));
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
      pd.timestamps.getStorage().force();
      pd.dataManager.flush(false);
    }
    final ExternalJavacManager server = ExternalJavacManager.KEY.get(context);
    if (server != null) {
      server.stop();
      ExternalJavacManager.KEY.set(context, null);
    }
  }

  private void runBuild(final CompileContextImpl context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info("Building project; isRebuild:" +
             context.isProjectRebuild() +
             "; isMake:" +
             context.isMake() +
             " parallel compilation:" +
             BuildRunner.PARALLEL_BUILD_ENABLED);

    context.addBuildListener(new ChainedTargetsBuildListener(context));
    
    //Deletes class loader classpath index files for changed output roots
    context.addBuildListener(new BuildListener() {
      @Override
      public void filesGenerated(FileGeneratedEvent event) {
        final Set<File> outputs = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
        for (Pair<String, String> pair : event.getPaths()) {
          outputs.add(new File(pair.getFirst()));
        }
        for (File root : outputs) {
          //noinspection ResultOfMethodCallIgnored
          new File(root, CLASSPATH_INDEX_FILE_NAME).delete();
        }
      }

      @Override
      public void filesDeleted(FileDeletedEvent event) {
      }
    });

    for (TargetBuilder builder : myBuilderRegistry.getTargetBuilders()) {
      builder.buildStarted(context);
    }
    for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
      builder.buildStarted(context);
    }

    try {
      // clean roots for targets for which rebuild is forced
      cleanOutputRoots(context, context.isProjectRebuild() || forceCleanCaches);

      context.processMessage(new ProgressMessage("Running 'before' tasks"));
      runTasks(context, myBuilderRegistry.getBeforeTasks());
      TimingLog.LOG.debug("'before' tasks finished");

      context.processMessage(new ProgressMessage("Checking sources"));
      buildChunks(context);
      TimingLog.LOG.debug("Building targets finished");

      context.processMessage(new ProgressMessage("Running 'after' tasks"));
      runTasks(context, myBuilderRegistry.getAfterTasks());
      TimingLog.LOG.debug("'after' tasks finished");
      sendElapsedTimeMessages(context);
    }
    finally {
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
    for (Map.Entry<Builder, AtomicLong> entry : myElapsedTimeNanosByBuilder.entrySet()) {
      AtomicInteger processedSourcesRef = myNumberOfSourcesProcessedByBuilder.get(entry.getKey());
      int processedSources = processedSourcesRef != null ? processedSourcesRef.get() : 0;
      context.processMessage(new BuilderStatisticsMessage(entry.getKey().getPresentableName(), processedSources, entry.getValue().get()/1000000));
    }
  }

  private void startTempDirectoryCleanupTask() {
    final String tempPath = System.getProperty("java.io.tmpdir", null);
    if (StringUtil.isEmptyOrSpaces(tempPath)) {
      return;
    }
    final File tempDir = new File(tempPath);
    final File dataRoot = myProjectDescriptor.dataManager.getDataPaths().getDataStorageRoot();
    if (!FileUtil.isAncestor(dataRoot, tempDir, true)) {
      // cleanup only 'local' temp
      return;
    }
    final File[] files = tempDir.listFiles();
    if (files != null && files.length != 0) {
      final RunnableFuture<Void> task = new FutureTask<>(() -> {
        for (File tempFile : files) {
          FileUtil.delete(tempFile);
        }
      }, null);
      final Thread thread = new Thread(task, "Temp directory cleanup");
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setDaemon(true);
      thread.start();
      myAsyncTasks.add(task);
    }
  }

  private CompileContextImpl createContext(CompileScope scope) throws ProjectBuildException {
    final CompileContextImpl context = new CompileContextImpl(scope, myProjectDescriptor, myMessageDispatcher,
                                                              myBuilderParams, myCancelStatus);

    // in project rebuild mode performance gain is hard to observe, so it is better to save memory
    // in make mode it is critical to traverse file system as fast as possible, so we choose speed over memory savings
    myProjectDescriptor.setFSCache(context.isProjectRebuild() ? FSCache.NO_CACHE : new FSCache());
    JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.set(context, myConstantSearch);
    return context;
  }

  private void cleanOutputRoots(CompileContext context, boolean cleanCaches) throws ProjectBuildException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    ProjectBuildException ex = null;
    try {
      final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(projectDescriptor.getProject());
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
    }
    catch (ProjectBuildException e) {
      ex = e;
    }
    finally {
      if (cleanCaches) {
        try {
          projectDescriptor.timestamps.getStorage().clean();
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
    }
  }

  public static void clearOutputFiles(CompileContext context, BuildTarget<?> target) throws IOException {
    final SourceToOutputMapping map = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
    final THashSet<File> dirsToDelete = target instanceof ModuleBasedTarget ? new THashSet<>(FileUtil.FILE_HASHING_STRATEGY) : null;
    for (String srcPath : map.getSources()) {
      final Collection<String> outs = map.getOutputs(srcPath);
      if (outs != null && !outs.isEmpty()) {
        List<String> deletedPaths = new ArrayList<>();
        for (String out : outs) {
          BuildOperations.deleteRecursively(out, deletedPaths, dirsToDelete);
        }
        if (!deletedPaths.isEmpty()) {
          context.processMessage(new FileDeletedEvent(deletedPaths));
        }
      }
    }
    registerTargetsWithClearedOutput(context, Collections.singletonList(target));
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

  private enum Applicability {
    NONE, PARTIAL, ALL;

    static <T> Applicability calculate(Predicate<T> p, Collection<T> collection) {
      int count = 0;
      int item = 0;
      for (T elem : collection) {
        item++;
        if (p.apply(elem)) {
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
    final long cleanStart = System.currentTimeMillis();
    final MultiMap<File, BuildTarget<?>> rootsToDelete = MultiMap.createSet();
    final Set<File> allSourceRoots = ContainerUtil.newTroveSet(FileUtil.FILE_HASHING_STRATEGY);

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
      boolean okToDelete = applicability == Applicability.ALL;
      if (okToDelete && !moduleIndex.isExcluded(outputRoot)) {
        // if output root itself is directly or indirectly excluded, 
        // there cannot be any manageable sources under it, even if the output root is located under some source root
        // so in this case it is safe to delete such root
        if (JpsPathUtil.isUnder(allSourceRoots, outputRoot)) {
          okToDelete = false;
        }
        else {
          final Set<File> _outRoot = ContainerUtil.newTroveSet(FileUtil.FILE_HASHING_STRATEGY, outputRoot);
          for (File srcRoot : allSourceRoots) {
            if (JpsPathUtil.isUnder(_outRoot, srcRoot)) {
              okToDelete = false;
              break;
            }
          }
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
        if (applicability == Applicability.ALL) {
          // only warn if unable to delete because of roots intersection
          context.processMessage(new CompilerMessage(
            "", BuildMessage.Kind.WARNING, "Output path " + outputRoot.getPath() + " intersects with a source root. Only files that were created by build will be cleaned.")
          );
        }
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
    LOG.info("Cleaned output directories in " + (System.currentTimeMillis() - cleanStart) + " ms");
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

  private static void runTasks(CompileContext context, final List<BuildTask> tasks) throws ProjectBuildException {
    for (BuildTask task : tasks) {
      task.build(context);
    }
  }

  private void buildChunks(final CompileContextImpl context) throws ProjectBuildException {
    try {
      final CompileScope scope = context.getScope();
      final ProjectDescriptor pd = context.getProjectDescriptor();
      final BuildTargetIndex targetIndex = pd.getBuildTargetIndex();

      // for better progress dynamics consider only actually affected chunks
      int totalAffected = 0;
      for (BuildTargetChunk chunk : targetIndex.getSortedTargetChunks(context)) {
        if (isAffected(context.getScope(), chunk)) {
          totalAffected += chunk.getTargets().size();
        }
      }
      myTotalTargetsWork = totalAffected;

      boolean compileInParallel = BuildRunner.PARALLEL_BUILD_ENABLED;
      if (compileInParallel && MAX_BUILDER_THREADS <= 1) {
        LOG.info("Switched off parallel compilation because maximum number of builder threads is less than 2. Set '"
                 + GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION + "' system property to a value greater than 1 to really enable parallel compilation.");
        compileInParallel = false;
      }

      if (compileInParallel) {
        new BuildParallelizer(context).buildInParallel();
      }
      else {
        // non-parallel build
        for (BuildTargetChunk chunk : targetIndex.getSortedTargetChunks(context)) {
          try {
            buildChunkIfAffected(context, scope, chunk);
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

  private static class BuildChunkTask {
    private final BuildTargetChunk myChunk;
    private final Set<BuildChunkTask> myNotBuiltDependencies = new THashSet<>();
    private final List<BuildChunkTask> myTasksDependsOnThis = new ArrayList<>();

    private BuildChunkTask(BuildTargetChunk chunk) {
      myChunk = chunk;
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

  private class BuildParallelizer {
    private final BoundedTaskExecutor myParallelBuildExecutor = new BoundedTaskExecutor("IncProjectBuilder executor pool", SharedThreadPool.getInstance(), MAX_BUILDER_THREADS);
    private final CompileContext myContext;
    private final AtomicReference<Throwable> myException = new AtomicReference<>();
    private final Object myQueueLock = new Object();
    private final CountDownLatch myTasksCountDown;
    private final List<BuildChunkTask> myTasks;

    private BuildParallelizer(CompileContext context) {
      myContext = context;
      final ProjectDescriptor pd = myContext.getProjectDescriptor();
      final BuildTargetIndex targetIndex = pd.getBuildTargetIndex();

      List<BuildTargetChunk> chunks = targetIndex.getSortedTargetChunks(myContext);
      myTasks = new ArrayList<>(chunks.size());
      Map<BuildTarget<?>, BuildChunkTask> targetToTask = new THashMap<>();
      for (BuildTargetChunk chunk : chunks) {
        BuildChunkTask task = new BuildChunkTask(chunk);
        myTasks.add(task);
        for (BuildTarget<?> target : chunk.getTargets()) {
          targetToTask.put(target, task);
        }
      }

      for (BuildChunkTask task : myTasks) {
        for (BuildTarget<?> target : task.getChunk().getTargets()) {
          for (BuildTarget<?> dependency : targetIndex.getDependencies(target, myContext)) {
            BuildChunkTask depTask = targetToTask.get(dependency);
            if (depTask != null && depTask != task) {
              task.addDependency(depTask);
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

    private void queueTasks(List<BuildChunkTask> tasks) {
      if (LOG.isDebugEnabled() && !tasks.isEmpty()) {
        final List<BuildTargetChunk> chunksToLog = new ArrayList<>();
        for (BuildChunkTask task : tasks) {
          chunksToLog.add(task.getChunk());
        }
        final StringBuilder logBuilder = new StringBuilder("Queuing " + chunksToLog.size() + " chunks in parallel: ");
        chunksToLog.sort(Comparator.comparing(BuildTargetChunk::toString));
        for (BuildTargetChunk chunk : chunksToLog) {
          logBuilder.append(chunk.toString()).append("; ");
        }
        LOG.debug(logBuilder.toString());
      }
      for (BuildChunkTask task : tasks) {
        queueTask(task);
      }
    }

    private void queueTask(final BuildChunkTask task) {
      final CompileContext chunkLocalContext = createContextWrapper(myContext);
      myParallelBuildExecutor.execute(() -> {
        try {
          try {
            if (myException.get() == null) {
              buildChunkIfAffected(chunkLocalContext, myContext.getScope(), task.getChunk());
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
      });
    }
  }

  private void buildChunkIfAffected(CompileContext context, CompileScope scope, BuildTargetChunk chunk) throws ProjectBuildException {
    if (isAffected(scope, chunk)) {
      buildTargetsChunk(context, chunk);
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

  private boolean runBuildersForChunk(final CompileContext context, final BuildTargetChunk chunk) throws ProjectBuildException, IOException {
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

      return runModuleLevelBuilders(context, new ModuleChunk(moduleTargets));
    }

    final BuildTarget<?> target = targets.iterator().next();
    if (target instanceof ModuleBuildTarget) {
      return runModuleLevelBuilders(context, new ModuleChunk(Collections.singleton((ModuleBuildTarget)target)));
    }

    // In general the set of files corresponding to changed source file may be different
    // Need this for example, to keep up with case changes in file names  for case-insensitive OSes: 
    // deleting the output before copying is the only way to ensure the case of the output file's name is exactly the same as source file's case
    cleanOldOutputs(context, target);
    
    final List<TargetBuilder<?, ?>> builders = BuilderRegistry.getInstance().getTargetBuilders();
    final float builderProgressDelta = 1.0f / builders.size();
    for (TargetBuilder<?, ?> builder : builders) {
      buildTarget(target, context, builder);
      updateDoneFraction(context, builderProgressDelta);
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
      //noinspection unchecked
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
        public void processDirtyFiles(@NotNull FileProcessor<T, BuildTarget<T>> processor) throws IOException {
          context.getProjectDescriptor().fsState.processFilesToRecompile(context, target, processor);
        }
      });
    }
  }
  
  
  private void updateDoneFraction(CompileContext context, final float delta) {
    myTargetsProcessed += delta;
    float processed = myTargetsProcessed;
    context.setDone(processed / myTotalTargetsWork);
  }

  private void buildTargetsChunk(CompileContext context, final BuildTargetChunk chunk) throws ProjectBuildException {
    final BuildFSState fsState = myProjectDescriptor.fsState;
    boolean doneSomething;
    try {
      context.setCompilationStartStamp(chunk.getTargets(), System.currentTimeMillis());

      sendBuildingTargetMessages(chunk.getTargets(), BuildingTargetProgressMessage.Event.STARTED);
      Utils.ERRORS_DETECTED_KEY.set(context, Boolean.FALSE);

      for (BuildTarget<?> target : chunk.getTargets()) {
        BuildOperations.ensureFSStateInitialized(context, target);
      }

      doneSomething = processDeletedPaths(context, chunk.getTargets());

      fsState.beforeChunkBuildStart(context, chunk);

      doneSomething |= runBuildersForChunk(context, chunk);

      fsState.clearContextRoundData(context);
      fsState.clearContextChunk(context);

      BuildOperations.markTargetsUpToDate(context, chunk);

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
  private boolean runModuleLevelBuilders(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException, IOException {
    for (BuilderCategory category : BuilderCategory.values()) {
      for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
        builder.chunkBuildStarted(context, chunk);
      }
    }

    boolean doneSomething = false;
    boolean rebuildFromScratchRequested = false;
    float stageCount = myTotalModuleLevelBuilderCount;
    final int modulesInChunk = chunk.getModules().size();
    int buildersPassed = 0;
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
                  LOG.info("Builder " + builder.getPresentableName() + " requested rebuild of module chunk " + chunk.getName());
                  // allow rebuild from scratch only once per chunk
                  rebuildFromScratchRequested = true;
                  try {
                    // forcibly mark all files in the chunk dirty
                    context.getProjectDescriptor().fsState.clearContextRoundData(context);
                    FSOperations.markDirty(context, CompilationRound.NEXT, chunk, null);
                    // reverting to the beginning
                    myTargetsProcessed -= (buildersPassed * modulesInChunk) / stageCount;
                    stageCount = myTotalModuleLevelBuilderCount;
                    buildersPassed = 0;
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
              updateDoneFraction(context, modulesInChunk / (stageCount));
            }
          }
          finally {
            final boolean moreToCompile = JavaBuilderUtil.updateMappingsOnRoundCompletion(context, dirtyFilesHolder, chunk);
            if (moreToCompile) {
              nextPassRequired = true;
            }
            if (nextPassRequired && !rebuildFromScratchRequested) {
              // recalculate basis
              myTargetsProcessed -= (buildersPassed * modulesInChunk) / stageCount;
              stageCount += myTotalModuleLevelBuilderCount;
              myTargetsProcessed += (buildersPassed * modulesInChunk) / stageCount;
            }
          }
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

  private void storeBuilderStatistics(Builder builder, long elapsedTime, int processedFiles) {
    if (!myElapsedTimeNanosByBuilder.containsKey(builder)) {
      myElapsedTimeNanosByBuilder.putIfAbsent(builder, new AtomicLong());
    }
    myElapsedTimeNanosByBuilder.get(builder).addAndGet(elapsedTime);

    if (!myNumberOfSourcesProcessedByBuilder.containsKey(builder)) {
      myNumberOfSourcesProcessedByBuilder.putIfAbsent(builder, new AtomicInteger());
    }
    myNumberOfSourcesProcessedByBuilder.get(builder).addAndGet(processedFiles);
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
