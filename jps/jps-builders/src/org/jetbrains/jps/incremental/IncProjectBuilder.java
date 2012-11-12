package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.MultiMapBasedOnSet;
import com.intellij.util.io.MappingFailedException;
import com.intellij.util.io.PersistentEnumerator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.java.ExternalJavacDescriptor;
import org.jetbrains.jps.incremental.messages.*;
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.service.SharedThreadPool;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.IncProjectBuilder");

  public static final String BUILD_NAME = "EXTERNAL BUILD";
  private static final String CLASSPATH_INDEX_FINE_NAME = "classpath.index";
  private static final boolean GENERATE_CLASSPATH_INDEX = Boolean.parseBoolean(System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION, "false"));
  private static final int MAX_BUILDER_THREADS;
  static {
    int maxThreads = 4;
    try {
      maxThreads = Math.max(2, Integer.parseInt(System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, "4")));
    }
    catch (NumberFormatException ignored) {
    }
    MAX_BUILDER_THREADS = maxThreads;
  }
  private final BoundedTaskExecutor myParallelBuildExecutor = new BoundedTaskExecutor(SharedThreadPool.getInstance(), Math.min(MAX_BUILDER_THREADS, Math.max(2, Runtime.getRuntime().availableProcessors())));

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  @Nullable private final Callbacks.ConstantAffectionResolver myConstantSearch;
  private final List<MessageHandler> myMessageHandlers = new ArrayList<MessageHandler>();
  private final MessageHandler myMessageDispatcher = new MessageHandler() {
    public void processMessage(BuildMessage msg) {
      for (MessageHandler h : myMessageHandlers) {
        h.processMessage(msg);
      }
    }
  };

  private volatile float myTargetsProcessed = 0.0f;
  private final float myTotalTargetsWork;
  private final int myTotalModuleLevelBuilderCount;
  private final List<Future> myAsyncTasks = new ArrayList<Future>();

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry, Map<String, String> builderParams, CanceledStatus cs,
                           @Nullable Callbacks.ConstantAffectionResolver constantSearch) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = cs;
    myConstantSearch = constantSearch;
    myTotalTargetsWork = pd.getBuildTargetIndex().getAllTargets().size();
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void checkUpToDate(CompileScope scope) {
    CompileContextImpl context = null;
    try {
      context = createContext(scope, true, false);
      final BuildFSState fsState = myProjectDescriptor.fsState;
      for (BuildTarget<?> target : myProjectDescriptor.getBuildTargetIndex().getAllTargets()) {
        if (scope.isAffected(target)) {
          BuildOperations.ensureFSStateInitialized(context, target);
          final Map<BuildRootDescriptor, Set<File>> toRecompile = fsState.getSourcesToRecompile(context, target);
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (toRecompile) {
            for (Set<File> files : toRecompile.values()) {
              for (File file : files) {
                if (scope.isAffected(target, file)) {
                  // this will serve as a marker that compiler has work to do
                  myMessageDispatcher.processMessage(DoneSomethingNotification.INSTANCE);
                  return;
                }
              }
            }
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


  public void build(CompileScope scope, final boolean isMake, final boolean isProjectRebuild, boolean forceCleanCaches)
    throws RebuildRequestedException {

    final LowMemoryWatcher memWatcher = LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        myProjectDescriptor.dataManager.flush(false);
        myProjectDescriptor.timestamps.getStorage().force();
      }
    });
    CompileContextImpl context = null;
    try {
      context = createContext(scope, isMake, isProjectRebuild);
      runBuild(context, forceCleanCaches);
      myProjectDescriptor.dataManager.saveVersion();
    }
    catch (ProjectBuildException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof PersistentEnumerator.CorruptedException ||
          cause instanceof MappingFailedException ||
          cause instanceof IOException) {
        myMessageDispatcher.processMessage(new CompilerMessage(
          BUILD_NAME, BuildMessage.Kind.INFO,
          "Internal caches are corrupted or have outdated format, forcing project rebuild: " +
          e.getMessage())
        );
        throw new RebuildRequestedException(cause);
      }
      else {
        if (cause == null) {
          final String msg = e.getMessage();
          if (!StringUtil.isEmpty(msg)) {
            myMessageDispatcher.processMessage(new ProgressMessage(msg));
          }
        }
        else {
          myMessageDispatcher.processMessage(new CompilerMessage(BUILD_NAME, cause));
        }
      }
    }
    finally {
      memWatcher.stop();
      flushContext(context);
      // wait for the async tasks
      for (Future task : myAsyncTasks) {
        try {
          task.get();
        }
        catch (Throwable th) {
          LOG.info(th);
        }
      }
    }
  }

  private static void flushContext(CompileContext context) {
    if (context != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.timestamps.getStorage().force();
      pd.dataManager.flush(false);
    }
    final ExternalJavacDescriptor descriptor = ExternalJavacDescriptor.KEY.get(context);
    if (descriptor != null) {
      try {
        final RequestFuture future = descriptor.client.sendShutdownRequest();
        future.waitFor(500L, TimeUnit.MILLISECONDS);
      }
      finally {
        // ensure process is not running
        descriptor.process.destroyProcess();
      }
      ExternalJavacDescriptor.KEY.set(context, null);
    }
    //cleanupJavacNameTable();
  }

  //private static boolean ourClenupFailed = false;

  //private static void cleanupJavacNameTable() {
  //  try {
  //    if (JavaBuilder.USE_EMBEDDED_JAVAC && !ourClenupFailed) {
  //      final Field freelistField = Class.forName("com.sun.tools.javac.util.Name$Table").getDeclaredField("freelist");
  //      freelistField.setAccessible(true);
  //      freelistField.set(null, com.sun.tools.javac.util.List.nil());
  //    }
  //  }
  //  catch (Throwable e) {
  //    ourClenupFailed = true;
  //    //LOG.info(e);
  //  }
  //}

  private void runBuild(CompileContextImpl context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info("Building project; isRebuild:" +
             context.isProjectRebuild() +
             "; isMake:" +
             context.isMake() +
             " parallel compilation:" +
             BuildRunner.PARALLEL_BUILD_ENABLED);

    for (TargetBuilder builder : myBuilderRegistry.getTargetBuilders()) {
      builder.buildStarted(context);
    }
    for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
      builder.buildStarted(context);
    }

    try {
      if (context.isProjectRebuild() || forceCleanCaches) {
        cleanOutputRoots(context);
      }

      context.processMessage(new ProgressMessage("Running 'before' tasks"));
      runTasks(context, myBuilderRegistry.getBeforeTasks());

      context.processMessage(new ProgressMessage("Checking sources"));
      buildChunks(context);

      context.processMessage(new ProgressMessage("Running 'after' tasks"));
      runTasks(context, myBuilderRegistry.getAfterTasks());

      // cleanup output roots layout, commented for efficiency
      //final ModuleOutputRootsLayout outputRootsLayout = context.getDataManager().getOutputRootsLayout();
      //try {
      //  final Iterator<String> keysIterator = outputRootsLayout.getKeysIterator();
      //  final Map<String, JpsModule> modules = myProjectDescriptor.project.getModules();
      //  while (keysIterator.hasNext()) {
      //    final String moduleName = keysIterator.next();
      //    if (modules.containsKey(moduleName)) {
      //      outputRootsLayout.remove(moduleName);
      //    }
      //  }
      //}
      //catch (IOException e) {
      //  throw new ProjectBuildException(e);
      //}
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

  private CompileContextImpl createContext(CompileScope scope, boolean isMake, final boolean isProjectRebuild)
    throws ProjectBuildException {
    final CompileContextImpl context = new CompileContextImpl(scope, myProjectDescriptor, isMake, isProjectRebuild, myMessageDispatcher,
                                                              myBuilderParams, myCancelStatus
    );
    JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.set(context, myConstantSearch);
    return context;
  }

  private void cleanOutputRoots(CompileContext context) throws ProjectBuildException {
    // whole project is affected
    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    JpsJavaCompilerConfiguration configuration =
      JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(projectDescriptor.getProject());
    final boolean shouldClear = configuration.isClearOutputDirectoryOnRebuild();
    try {
      if (shouldClear) {
        clearOutputs(context);
      }
      else {
        for (BuildTarget<?> target : projectDescriptor.getBuildTargetIndex().getAllTargets()) {
          clearOutputFiles(context, target);
        }
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning output files", e);
    }

    try {
      projectDescriptor.timestamps.getStorage().clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning timestamps storage", e);
    }
    try {
      projectDescriptor.dataManager.clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning compiler storages", e);
    }
    myProjectDescriptor.fsState.clearAll();
  }

  public static void clearOutputFiles(CompileContext context, BuildTarget<?> target) throws IOException {
    final SourceToOutputMapping map = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
    final THashSet<File> dirsToDelete = target instanceof ModuleBasedTarget? new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY) : null;
    for (String srcPath : map.getSources()) {
      final Collection<String> outs = map.getOutputs(srcPath);
      if (outs != null && !outs.isEmpty()) {
        for (String out : outs) {
          final File outFile = new File(out);
          final boolean deleted = outFile.delete();
          if (deleted && dirsToDelete != null) {
            final File parent = outFile.getParentFile();
            if (parent != null) {
              dirsToDelete.add(parent);
            }
          }
        }
        context.processMessage(new FileDeletedEvent(outs));
      }
    }
    FSOperations.pruneEmptyDirs(dirsToDelete);
  }

  private void clearOutputs(CompileContext context) throws ProjectBuildException, IOException {
    final MultiMap<File, BuildTarget<?>> rootsToDelete = new MultiMapBasedOnSet<File, BuildTarget<?>>();
    final Set<File> allSourceRoots = new HashSet<File>();

    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    for (BuildTarget<?> target : projectDescriptor.getBuildTargetIndex().getAllTargets()) {
      final Collection<File> outputs = target.getOutputRoots(context);
      for (File file : outputs) {
        rootsToDelete.putValue(file, target);
      }
    }

    for (BuildTargetType<?> type : JavaModuleBuildTargetType.ALL_TYPES) {
      for (BuildTarget<?> target : projectDescriptor.getBuildTargetIndex().getAllTargets(type)) {
        for (BuildRootDescriptor descriptor : projectDescriptor.getBuildRootIndex().getTargetRoots(target, context)) {
          if (!descriptor.isGenerated()) {
            // excluding from checks source roots with generated sources; because it is safe to delete generated stuff
            allSourceRoots.add(descriptor.getRootFile());
          }
        }
      }
    }

    // check that output and source roots are not overlapping
    final List<File> filesToDelete = new ArrayList<File>();
    for (Map.Entry<File, Collection<BuildTarget<?>>> entry : rootsToDelete.entrySet()) {
      context.checkCanceled();
      boolean okToDelete = true;
      final File outputRoot = entry.getKey();
      if (JpsPathUtil.isUnder(allSourceRoots, outputRoot)) {
        okToDelete = false;
      }
      else {
        final Set<File> _outRoot = Collections.singleton(outputRoot);
        for (File srcRoot : allSourceRoots) {
          if (JpsPathUtil.isUnder(_outRoot, srcRoot)) {
            okToDelete = false;
            break;
          }
        }
      }
      if (okToDelete) {
        // do not delete output root itself to avoid lots of unnecessary "roots_changed" events in IDEA
        final File[] children = outputRoot.listFiles();
        if (children != null) {
          filesToDelete.addAll(Arrays.asList(children));
        }
        else if (outputRoot.isFile()) {
          filesToDelete.add(outputRoot);
        }
      }
      else {
        context.processMessage(new CompilerMessage(
          "", BuildMessage.Kind.WARNING, "Output path " + outputRoot.getPath() + " intersects with a source root. Only files that were created by build will be cleaned.")
        );
        // clean only those files we are aware of
        for (BuildTarget<?> target : entry.getValue()) {
          clearOutputFiles(context, target);
        }
      }
    }

    context.processMessage(new ProgressMessage("Cleaning output directories..."));
    myAsyncTasks.add(
      FileUtil.asyncDelete(filesToDelete)
    );
  }

  private static void runTasks(CompileContext context, final List<BuildTask> tasks) throws ProjectBuildException {
    for (BuildTask task : tasks) {
      task.build(context);
    }
  }

  private void buildChunks(final CompileContextImpl context) throws ProjectBuildException {
    final CompileScope scope = context.getScope();
    final ProjectDescriptor pd = context.getProjectDescriptor();
    BuildTargetIndex targetIndex = pd.getBuildTargetIndex();
    try {
      if (BuildRunner.PARALLEL_BUILD_ENABLED) {
        final List<ChunkGroup> chunkGroups = buildChunkGroups(targetIndex);
        for (ChunkGroup group : chunkGroups) {
          final List<BuildTargetChunk> groupChunks = group.getChunks();
          final int chunkCount = groupChunks.size();
          if (chunkCount == 0) {
            continue;
          }
          try {
            if (chunkCount == 1) {
              buildChunkIfAffected(createContextWrapper(context), scope, groupChunks.iterator().next());
            }
            else {
              final CountDownLatch latch = new CountDownLatch(chunkCount);
              final Ref<Throwable> exRef = new Ref<Throwable>(null);

              if (LOG.isDebugEnabled()) {
                final StringBuilder logBuilder = new StringBuilder("Building chunks in parallel: ");
                for (BuildTargetChunk chunk : groupChunks) {
                  logBuilder.append(chunk.toString()).append("; ");
                }
                LOG.debug(logBuilder.toString());
              }

              for (final BuildTargetChunk chunk : groupChunks) {
                final CompileContext chunkLocalContext = createContextWrapper(context);
                myParallelBuildExecutor.execute(new Runnable() {
                  @Override
                  public void run() {
                    try {
                      buildChunkIfAffected(chunkLocalContext, scope, chunk);
                    }
                    catch (Throwable e) {
                      synchronized (exRef) {
                        if (exRef.isNull()) {
                          exRef.set(e);
                        }
                      }
                      LOG.info(e);
                    }
                    finally {
                      latch.countDown();
                    }
                  }
                });
              }

              try {
                latch.await();
              }
              catch (InterruptedException e) {
                LOG.info(e);
              }

              final Throwable exception = exRef.get();
              if (exception != null) {
                if (exception instanceof ProjectBuildException) {
                  throw (ProjectBuildException)exception;
                }
                else {
                  throw new ProjectBuildException(exception);
                }
              }
            }
          }
          finally {
            context.updateCompilationStartStamp();
            pd.dataManager.closeSourceToOutputStorages(groupChunks);
            pd.dataManager.flush(true);
          }
        }
      }
      else {
        // non-parallel build
        for (BuildTargetChunk chunk : targetIndex.getSortedTargetChunks()) {
          try {
            buildChunkIfAffected(context, scope, chunk);
          }
          finally {
            context.updateCompilationStartStamp();
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

  private void buildChunkIfAffected(CompileContext context, CompileScope scope, BuildTargetChunk chunk) throws ProjectBuildException {
    if (isAffected(scope, chunk)) {
      buildTargetsChunk(context, chunk);
    }
    else {
      updateDoneFraction(context, chunk.getTargets().size());
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

  private boolean runBuildersForChunk(CompileContext context, final BuildTargetChunk chunk) throws ProjectBuildException, IOException {
    Set<? extends BuildTarget<?>> targets = chunk.getTargets();
    if (targets.size() > 1) {
      Set<ModuleBuildTarget> moduleTargets = new HashSet<ModuleBuildTarget>();
      for (BuildTarget<?> target : targets) {
        if (target instanceof ModuleBuildTarget) {
          moduleTargets.add((ModuleBuildTarget)target);
        }
        else {
          context.processMessage(new CompilerMessage(
            BUILD_NAME, BuildMessage.Kind.ERROR, "Cannot build " + target.getPresentableName() + " because it is included into a circular dependency")
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

    final List<TargetBuilder<?, ?>> builders = BuilderRegistry.getInstance().getTargetBuilders();
    for (TargetBuilder<?, ?> builder : builders) {
      BuildOperations.buildTarget(target, context, builder);
      updateDoneFraction(context, 1.0f / builders.size());
    }
    return true;
  }

  private void updateDoneFraction(CompileContext context, final float delta) {
    myTargetsProcessed += delta;
    float processed = myTargetsProcessed;
    context.setDone(processed / myTotalTargetsWork);
  }

  private void buildTargetsChunk(CompileContext context, final BuildTargetChunk chunk) throws ProjectBuildException {

    boolean doneSomething = false;
    try {
      Utils.ERRORS_DETECTED_KEY.set(context, Boolean.FALSE);
      BuildOperations.ensureFSStateInitialized(context, chunk);
      if (context.isMake()) {
        doneSomething |= processDeletedPaths(context, chunk.getTargets());
      }

      myProjectDescriptor.fsState.beforeChunkBuildStart(context, chunk);

      doneSomething = runBuildersForChunk(context, chunk);

      onChunkBuildComplete(context, chunk);

      if (doneSomething && GENERATE_CLASSPATH_INDEX) {
        myAsyncTasks.add(SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            createClasspathIndex(chunk);
          }
        }));
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      for (BuildRootDescriptor rd : context.getProjectDescriptor().getBuildRootIndex().clearTempRoots(context)) {
        context.getProjectDescriptor().fsState.clearRecompile(rd);
      }
      try {
        // restore deleted paths that were not procesesd by 'integrate'
        final Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
        if (map != null) {
          for (Map.Entry<BuildTarget<?>, Collection<String>> entry : map.entrySet()) {
            final BuildTarget<?> target = entry.getKey();
            final Collection<String> paths = entry.getValue();
            if (paths != null) {
              for (String path : paths) {
                myProjectDescriptor.fsState.registerDeleted(target, new File(path), null);
              }
            }
          }
        }
      }
      catch (IOException e) {
        throw new ProjectBuildException(e);
      }
      finally {
        Utils.REMOVED_SOURCES_KEY.set(context, null);
      }
    }
  }

  private static void createClasspathIndex(final BuildTargetChunk chunk) {
    final Set<File> outputDirs = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    for (BuildTarget<?> target : chunk.getTargets()) {
      if (target instanceof ModuleBuildTarget) {
        File outputDir = ((ModuleBuildTarget)target).getOutputDir();
        if (outputDir != null && outputDirs.add(outputDir)) {
          try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputDir, CLASSPATH_INDEX_FINE_NAME)));
            try {
              writeIndex(writer, outputDir, "");
            }
            finally {
              writer.close();
            }
          }
          catch (IOException e) {
            // Ignore. Failed to create optional classpath index
          }
        }
      }
    }
  }

  private static void writeIndex(final BufferedWriter writer, final File file, final String path) throws IOException {
    writer.write(path);
    writer.write('\n');
    final File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        final String _path = path.isEmpty() ? child.getName() : path + "/" + child.getName();
        writeIndex(writer, child, _path);
      }
    }
  }


  private boolean processDeletedPaths(CompileContext context, final Set<? extends BuildTarget<?>> targets) throws ProjectBuildException {
    boolean doneSomething = false;
    try {
      // cleanup outputs
      final Map<BuildTarget<?>, Collection<String>> targetToRemovedSources = new HashMap<BuildTarget<?>, Collection<String>>();

      final THashSet<File> dirsToDelete = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      for (BuildTarget<?> target : targets) {
        final Collection<String> deletedPaths = myProjectDescriptor.fsState.getAndClearDeletedPaths(target);
        if (deletedPaths.isEmpty()) {
          continue;
        }
        targetToRemovedSources.put(target, deletedPaths);
        final boolean shouldPruneEmptyDirs = target instanceof ModuleBasedTarget;
        final SourceToOutputMapping sourceToOutputStorage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
        final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        // actually delete outputs associated with removed paths
        for (String deletedSource : deletedPaths) {
          // deleting outputs corresponding to non-existing source
          final Collection<String> outputs = sourceToOutputStorage.getOutputs(deletedSource);

          if (outputs != null && !outputs.isEmpty()) {
            if (logger.isEnabled()) {
              logger.logDeletedFiles(outputs);
            }

            for (String output : outputs) {
              final File outFile = new File(output);
              final boolean deleted = outFile.delete();
              if (deleted) {
                doneSomething = true;
                if (shouldPruneEmptyDirs) {
                  final File parent = outFile.getParentFile();
                  if (parent != null) {
                    dirsToDelete.add(parent);
                  }
                }
              }
            }
            context.processMessage(new FileDeletedEvent(outputs));
          }

          if (target instanceof ModuleBuildTarget) {
            // check if deleted source was associated with a form
            final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
            final Collection<String> boundForms = sourceToFormMap.getState(deletedSource);
            if (boundForms != null) {
              for (String formPath : boundForms) {
                final File formFile = new File(formPath);
                if (formFile.exists()) {
                  FSOperations.markDirty(context, formFile);
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

      FSOperations.pruneEmptyDirs(dirsToDelete);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
    return doneSomething;
  }

  // return true if changed something, false otherwise
  private boolean runModuleLevelBuilders(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException, IOException {
    boolean doneSomething = false;
    boolean rebuildFromScratchRequested = false;
    float stageCount = myTotalModuleLevelBuilderCount;
    final int modulesInChunk = chunk.getModules().size();
    int buildersPassed = 0;
    boolean nextPassRequired;
    BuildOperations.ChunkBuildOutputConsumerImpl outputConsumer = new BuildOperations.ChunkBuildOutputConsumerImpl(context);
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
        if (!context.isProjectRebuild()) {
          final Map<ModuleBuildTarget, Set<File>> cleanedSources = FSOperations
            .cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder);
          for (Map.Entry<ModuleBuildTarget, Set<File>> entry : cleanedSources.entrySet()) {
            final ModuleBuildTarget target = entry.getKey();
            final Set<File> files = entry.getValue();
            if (!files.isEmpty()) {
              final SourceToOutputMapping mapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
              for (File srcFile : files) {
                mapping.setOutputs(srcFile.getPath(), Collections.<String>emptyList());
              }
            }
          }
        }

        BUILDER_CATEGORY_LOOP:
        for (BuilderCategory category : BuilderCategory.values()) {
          final List<ModuleLevelBuilder> builders = myBuilderRegistry.getBuilders(category);
          if (builders.isEmpty()) {
            continue;
          }

          for (ModuleLevelBuilder builder : builders) {
            if (context.isMake()) {
              processDeletedPaths(context, chunk.getTargets());
            }
            final ModuleLevelBuilder.ExitCode buildResult = builder.build(context, chunk, dirtyFilesHolder, outputConsumer);

            doneSomething |= (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE);

            if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
              throw new ProjectBuildException("Builder " + builder.getPresentableName() + " requested build stop");
            }
            context.checkCanceled();
            if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
              if (!nextPassRequired) {
                // recalculate basis
                myTargetsProcessed -= (buildersPassed * modulesInChunk) / stageCount;
                stageCount += myTotalModuleLevelBuilderCount;
                myTargetsProcessed += (buildersPassed * modulesInChunk) / stageCount;
              }
              nextPassRequired = true;
            }
            else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
              if (!rebuildFromScratchRequested && !context.isProjectRebuild()) {
                LOG.info("Builder " + builder.getPresentableName() + " requested rebuild of module chunk " + chunk.getName());
                // allow rebuild from scratch only once per chunk
                rebuildFromScratchRequested = true;
                try {
                  // forcibly mark all files in the chunk dirty
                  FSOperations.markDirty(context, chunk);
                  // reverting to the beginning
                  myTargetsProcessed -= (buildersPassed * modulesInChunk) / stageCount;
                  stageCount = myTotalModuleLevelBuilderCount;
                  buildersPassed = 0;
                  nextPassRequired = true;
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
      }
      while (nextPassRequired);
    }
    finally {
      outputConsumer.fireFileGeneratedEvents();
      for (BuilderCategory category : BuilderCategory.values()) {
        for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
          builder.cleanupChunkResources(context);
        }
      }
    }

    return doneSomething;
  }

  private static List<ChunkGroup> buildChunkGroups(BuildTargetIndex index) {
    final List<BuildTargetChunk> allChunks = index.getSortedTargetChunks();

    // building aux dependencies map
    final Map<BuildTarget<?>, Set<BuildTarget<?>>> depsMap = new HashMap<BuildTarget<?>, Set<BuildTarget<?>>>();
    for (BuildTarget target : index.getAllTargets()) {
      depsMap.put(target, index.getDependenciesRecursively(target));
    }

    final List<ChunkGroup> groups = new ArrayList<ChunkGroup>();
    ChunkGroup currentGroup = new ChunkGroup();
    groups.add(currentGroup);
    for (BuildTargetChunk chunk : allChunks) {
      if (dependsOnGroup(chunk, currentGroup, depsMap)) {
        currentGroup = new ChunkGroup();
        groups.add(currentGroup);
      }
      currentGroup.addChunk(chunk);
    }
    return groups;
  }


  private static boolean dependsOnGroup(BuildTargetChunk chunk, ChunkGroup group, Map<BuildTarget<?>, Set<BuildTarget<?>>> depsMap) {
    for (BuildTargetChunk groupChunk : group.getChunks()) {
      final Set<? extends BuildTarget<?>> groupChunkTargets = groupChunk.getTargets();
      for (BuildTarget<?> target : chunk.getTargets()) {
        if (ContainerUtil.intersects(depsMap.get(target), groupChunkTargets)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void onChunkBuildComplete(CompileContext context, @NotNull BuildTargetChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final BuildFSState fsState = pd.fsState;
    fsState.clearContextRoundData(context);
    fsState.clearContextChunk(context);

    BuildOperations.markTargetsUpToDate(context, chunk);
  }

  private static class ChunkGroup {
    private final List<BuildTargetChunk> myChunks = new ArrayList<BuildTargetChunk>();

    public void addChunk(BuildTargetChunk chunk) {
      myChunks.add(chunk);
    }

    public List<BuildTargetChunk> getChunks() {
      return myChunks;
    }
  }

  private static final Set<Key> GLOBAL_CONTEXT_KEYS = new HashSet<Key>();
  static {
    // keys for data that must be visible to all threads
    GLOBAL_CONTEXT_KEYS.add(ExternalJavacDescriptor.KEY);
  }

  private static CompileContext createContextWrapper(final CompileContext delegate) {
    final ClassLoader loader = delegate.getClass().getClassLoader();
    final UserDataHolderBase localDataHolder = new UserDataHolderBase();
    final Set deletedKeysSet = new ConcurrentHashSet();
    final Class<UserDataHolder> dataHolderinterface = UserDataHolder.class;
    final Class<MessageHandler> messageHandlerinterface = MessageHandler.class;
    return (CompileContext)Proxy.newProxyInstance(loader, new Class[]{CompileContext.class}, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();
        if (dataHolderinterface.equals(declaringClass)) {
          final Object firstArgument = args[0];
          final boolean isGlobalContextKey = firstArgument instanceof Key && GLOBAL_CONTEXT_KEYS.contains((Key)firstArgument);
          if (!isGlobalContextKey) {
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
        else if (messageHandlerinterface.equals(declaringClass)) {
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
