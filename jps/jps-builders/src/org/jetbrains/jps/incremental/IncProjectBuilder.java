package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.io.MappingFailedException;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.api.SharedThreadPool;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.java.ExternalJavacDescriptor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.messages.UptoDateFilesSavedEvent;
import org.jetbrains.jps.incremental.storage.*;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
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
  private static final boolean PARALLEL_BUILD_ENABLED = Boolean.parseBoolean(System.getProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "false"));

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  @Nullable private final Callbacks.ConstantAffectionResolver myConstantSearch;
  private ProjectChunks myProductionChunks;
  private ProjectChunks myTestChunks;
  private final List<MessageHandler> myMessageHandlers = new ArrayList<MessageHandler>();
  private final MessageHandler myMessageDispatcher = new MessageHandler() {
    public void processMessage(BuildMessage msg) {
      for (MessageHandler h : myMessageHandlers) {
        h.processMessage(msg);
      }
    }
  };

  private volatile float myModulesProcessed = 0.0f;
  private final float myTotalModulesWork;
  private final int myTotalModuleLevelBuilderCount;
  private final List<Future> myAsyncTasks = new ArrayList<Future>();

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry, Map<String, String> builderParams, CanceledStatus cs,
                           @Nullable Callbacks.ConstantAffectionResolver constantSearch) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = cs;
    myConstantSearch = constantSearch;
    myProductionChunks = new ProjectChunks(pd.jpsProject, JpsJavaClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(pd.jpsProject, JpsJavaClasspathKind.TEST_COMPILE);
    myTotalModulesWork = (float)pd.rootsIndex.getTotalModuleCount() * 2;  /* multiply by 2 to reflect production and test sources */
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
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

  private static boolean ourClenupFailed = false;

  private static void cleanupJavacNameTable() {
    try {
      if (JavaBuilder.USE_EMBEDDED_JAVAC && !ourClenupFailed) {
        final Field freelistField = Class.forName("com.sun.tools.javac.util.Name$Table").getDeclaredField("freelist");
        freelistField.setAccessible(true);
        freelistField.set(null, com.sun.tools.javac.util.List.nil());
      }
    }
    catch (Throwable e) {
      ourClenupFailed = true;
      //LOG.info(e);
    }
  }

  private float updateFractionBuilderFinished(final float delta) {
    myModulesProcessed += delta;
    float processed = myModulesProcessed;
    return processed / myTotalModulesWork;
  }

  private void runBuild(CompileContextImpl context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info("Building project '" + context.getProjectDescriptor().project.getProjectName() + "'; isRebuild:" + context.isProjectRebuild() + "; isMake:" + context.isMake() + " parallel compilation:" + PARALLEL_BUILD_ENABLED);

    for (ProjectLevelBuilder builder : myBuilderRegistry.getProjectLevelBuilders()) {
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

      context.setCompilingTests(false);
      context.processMessage(new ProgressMessage("Checking production sources"));
      buildChunks(context, myProductionChunks);

      context.setCompilingTests(true);
      context.processMessage(new ProgressMessage("Checking test sources"));
      buildChunks(context, myTestChunks);

      context.processMessage(new ProgressMessage("Building project"));
      runProjectLevelBuilders(context);

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
      for (ProjectLevelBuilder builder : myBuilderRegistry.getProjectLevelBuilders()) {
        builder.buildFinished(context);
      }
      for (ModuleLevelBuilder builder : myBuilderRegistry.getModuleLevelBuilders()) {
        builder.buildFinished(context);
      }
      context.processMessage(new ProgressMessage("Finished, saving caches..."));
    }

  }

  private CompileContextImpl createContext(CompileScope scope, boolean isMake, final boolean isProjectRebuild) throws ProjectBuildException {
    final CompileContextImpl context = new CompileContextImpl(
      scope, myProjectDescriptor, isMake, isProjectRebuild, myProductionChunks, myTestChunks, myMessageDispatcher,
      myBuilderParams, myCancelStatus
    );
    ModuleLevelBuilder.CONSTANT_SEARCH_SERVICE.set(context, myConstantSearch);
    return context;
  }

  private void cleanOutputRoots(CompileContext context) throws ProjectBuildException {
    // whole project is affected
    final boolean shouldClear = context.getProjectDescriptor().project.getCompilerConfiguration().isClearOutputDirectoryOnRebuild();
    try {
      if (shouldClear) {
        clearOutputs(context);
      }
      else {
        for (JpsModule module : context.getProjectDescriptor().jpsProject.getModules()) {
          final String moduleName = module.getName();
          clearOutputFiles(context, moduleName, true);
          clearOutputFiles(context, moduleName, false);
        }
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning output files", e);
    }

    try {
      context.getProjectDescriptor().timestamps.getStorage().clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning timestamps storage", e);
    }
    try {
      context.getProjectDescriptor().dataManager.clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning compiler storages", e);
    }
    myProjectDescriptor.fsState.clearAll();
  }

  private static void clearOutputFiles(CompileContext context, final String moduleName, boolean forTests) throws IOException {
    final SourceToOutputMapping map = context.getProjectDescriptor().dataManager.getSourceToOutputMap(moduleName, forTests);
    for (String srcPath : map.getKeys()) {
      final Collection<String> outs = map.getState(srcPath);
      if (outs != null) {
        for (String out : outs) {
          new File(out).delete();
        }
      }
    }
  }

  private void clearOutputs(CompileContext context) throws ProjectBuildException, IOException {
    final Collection<JpsModule> modulesToClean = context.getProjectDescriptor().jpsProject.getModules();
    final Map<File, Set<Pair<String, Boolean>>> rootsToDelete = new HashMap<File, Set<Pair<String, Boolean>>>(); // map: outputRoot-> setOfPairs([module, isTest])
    final Set<File> annotationOutputs = new HashSet<File>(); // separate collection because no root intersection checks needed for annotation generated sources
    final Set<File> allSourceRoots = new HashSet<File>();

    final ProjectPaths paths = context.getProjectPaths();

    for (JpsModule module : modulesToClean) {
      final File out = paths.getModuleOutputDir(module, false);
      if (out != null) {
        appendRootInfo(rootsToDelete, out, module, false);
      }
      final File testOut = paths.getModuleOutputDir(module, true);
      if (testOut != null) {
        appendRootInfo(rootsToDelete, testOut, module, true);
      }

      final AnnotationProcessingProfile profile = context.getAnnotationProcessingProfile(module);
      if (profile.isEnabled()) {
        File annotationOut =
          paths.getAnnotationProcessorGeneratedSourcesOutputDir(module, false, profile.getGeneratedSourcesDirName());
        if (annotationOut != null) {
          annotationOutputs.add(annotationOut);
        }
        annotationOut =
          paths.getAnnotationProcessorGeneratedSourcesOutputDir(module, true, profile.getGeneratedSourcesDirName());
        if (annotationOut != null) {
          annotationOutputs.add(annotationOut);
        }
      }

      final List<RootDescriptor> moduleRoots = context.getProjectDescriptor().rootsIndex.getModuleRoots(context, module);
      for (RootDescriptor d : moduleRoots) {
        allSourceRoots.add(d.root);
      }
    }

    // check that output and source roots are not overlapping
    final List<File> filesToDelete = new ArrayList<File>();
    for (Map.Entry<File, Set<Pair<String, Boolean>>> entry : rootsToDelete.entrySet()) {
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
      }
      else {
        context.processMessage(new CompilerMessage(BUILD_NAME, BuildMessage.Kind.WARNING, "Output path " +
                                                                                                   outputRoot.getPath() +
                                                                                                   " intersects with a source root. The output cannot be cleaned."));
        // clean only those files we are aware of
        for (Pair<String, Boolean> info : entry.getValue()) {
          clearOutputFiles(context, info.first, info.second);
        }
      }
    }

    for (File annotationOutput : annotationOutputs) {
      // do not delete output root itself to avoid lots of unnecessary "roots_changed" events in IDEA
      final File[] children = annotationOutput.listFiles();
      if (children != null) {
        filesToDelete.addAll(Arrays.asList(children));
      }
    }

    context.processMessage(new ProgressMessage("Cleaning output directories..."));
    myAsyncTasks.add(
      FileUtil.asyncDelete(filesToDelete)
    );
  }

  private static void appendRootInfo(Map<File, Set<Pair<String, Boolean>>> rootsToDelete, File out, JpsModule module, boolean isTest) {
    Set<Pair<String, Boolean>> infos = rootsToDelete.get(out);
    if (infos == null) {
      infos = new HashSet<Pair<String, Boolean>>();
      rootsToDelete.put(out, infos);
    }
    infos.add(Pair.create(module.getName(), isTest));
  }

  private static void runTasks(CompileContext context, final List<BuildTask> tasks) throws ProjectBuildException {
    for (BuildTask task : tasks) {
      task.build(context);
    }
  }

  private void buildChunks(final CompileContextImpl context, ProjectChunks chunks) throws ProjectBuildException {
    final CompileScope scope = context.getScope();
    final ProjectDescriptor pd = context.getProjectDescriptor();
    try {
      if (PARALLEL_BUILD_ENABLED) {
        final List<ChunkGroup> chunkGroups = buildChunkGroups(context, chunks);
        for (ChunkGroup group : chunkGroups) {
          final List<ModuleChunk> groupChunks = group.getChunks();
          final int chunkCount = groupChunks.size();
          if (chunkCount == 0) {
            continue;
          }
          try {
            if (chunkCount == 1) {
              _buildChunk(createContextWrapper(context), scope, groupChunks.iterator().next());
            }
            else {
              final CountDownLatch latch = new CountDownLatch(chunkCount);
              final Ref<Throwable> exRef = new Ref<Throwable>(null);

              if (LOG.isDebugEnabled()) {
                final StringBuilder logBuilder = new StringBuilder("Building chunks in parallel: ");
                for (ModuleChunk chunk : groupChunks) {
                  logBuilder.append(chunk.getName()).append("; ");
                }
                LOG.debug(logBuilder.toString());
              }

              for (final ModuleChunk chunk : groupChunks) {
                final CompileContext chunkLocalContext = createContextWrapper(context);
                SharedThreadPool.INSTANCE.submitBuildTask(new Runnable() {
                  @Override
                  public void run() {
                    try {
                      _buildChunk(chunkLocalContext, scope, chunk);
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
            pd.dataManager.closeSourceToOutputStorages(groupChunks, context.isCompilingTests());
            pd.dataManager.flush(true);
          }
        }
      }
      else {
        // non-parallel build
        for (ModuleChunk chunk : chunks.getChunkList()) {
          try {
            _buildChunk(context, scope, chunk);
          }
          finally {
            pd.dataManager.closeSourceToOutputStorages(Collections.singleton(chunk), context.isCompilingTests());
            pd.dataManager.flush(true);
          }
        }
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private void _buildChunk(CompileContext context, CompileScope scope, ModuleChunk chunk) throws ProjectBuildException {
    if (scope.isAffected(chunk)) {
      buildChunk(context, chunk);
    }
    else {
      final float fraction = updateFractionBuilderFinished(chunk.getModules().size());
      context.setDone(fraction);
    }
  }

  private void buildChunk(CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    boolean doneSomething = false;
    try {
      Utils.ERRORS_DETECTED_KEY.set(context, Boolean.FALSE);
      ensureFSStateInitialized(context, chunk);
      if (context.isMake()) {
        processDeletedPaths(context, chunk);
        doneSomething |= Utils.hasRemovedSources(context);
      }

      myProjectDescriptor.fsState.beforeChunkBuildStart(context, chunk);

      doneSomething = runModuleLevelBuilders(context, chunk);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      try {
        for (BuilderCategory category : BuilderCategory.values()) {
          for (ModuleLevelBuilder builder : myBuilderRegistry.getBuilders(category)) {
            builder.cleanupResources(context, chunk);
          }
        }
      }
      finally {
        try {
          onChunkBuildComplete(context, chunk);
        }
        catch (Exception e) {
          throw new ProjectBuildException(e);
        }
        finally {
          final Collection<RootDescriptor> tempRoots = context.getProjectDescriptor().rootsIndex.clearTempRoots(context);
          if (!tempRoots.isEmpty()) {
            final Set<File> rootFiles = new HashSet<File>();
            for (RootDescriptor rd : tempRoots) {
              rootFiles.add(rd.root);
              context.getProjectDescriptor().fsState.clearRecompile(rd);
            }
            myAsyncTasks.add(
              FileUtil.asyncDelete(rootFiles)
            );
          }

          try {
            // restore deleted paths that were not procesesd by 'integrate'
            final Map<String, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
            if (map != null) {
              final boolean forTests = context.isCompilingTests();
              for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
                final String moduleName = entry.getKey();
                final Collection<String> paths = entry.getValue();
                if (paths != null) {
                  for (String path : paths) {
                    myProjectDescriptor.fsState.registerDeleted(moduleName, new File(path), forTests, null);
                  }
                }
              }
            }
          }
          catch (IOException e) {
            throw new ProjectBuildException(e);
          }

          Utils.REMOVED_SOURCES_KEY.set(context, null);

          if (doneSomething && GENERATE_CLASSPATH_INDEX) {
            final boolean forTests = context.isCompilingTests();
            final Future<?> future = SharedThreadPool.INSTANCE.submit(new Runnable() {
              @Override
              public void run() {
                createClasspathIndex(chunk, forTests);
              }
            });
            myAsyncTasks.add(future);
          }
        }
      }
    }
  }

  private static void createClasspathIndex(final ModuleChunk chunk, boolean forTests) {
    final Set<File> outputPaths = new LinkedHashSet<File>();
    for (JpsModule module : chunk.getModules()) {
      final String outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, forTests);
      if (outputUrl != null) {
        outputPaths.add(JpsPathUtil.urlToFile(outputUrl));
      }
    }
    for (File outputRoot : outputPaths) {
      try {
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputRoot, CLASSPATH_INDEX_FINE_NAME)));
        try {
          writeIndex(writer, outputRoot, "");
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


  private void processDeletedPaths(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    try {
      // cleanup outputs
      final Map<String, Collection<String>> removedSources = new HashMap<String, Collection<String>>();

      for (JpsModule module : chunk.getModules()) {
        final Collection<String> deletedPaths = myProjectDescriptor.fsState.getAndClearDeletedPaths(module.getName(),
                                                                                                    context.isCompilingTests());
        if (deletedPaths.isEmpty()) {
          continue;
        }
        removedSources.put(module.getName(), deletedPaths);

        final SourceToOutputMapping sourceToOutputStorage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(module.getName(), context.isCompilingTests());
        // actually delete outputs associated with removed paths
        for (String deletedSource : deletedPaths) {
          // deleting outputs corresponding to non-existing source
          final Collection<String> outputs = sourceToOutputStorage.getState(deletedSource);

          if (outputs != null) {
            final JavaBuilderLogger logger = context.getLoggingManager().getJavaBuilderLogger();
            if (logger.isEnabled()) {
              if (outputs.size() > 0) {
                final String[] buffer = new String[outputs.size()];
                int i = 0;
                for (final String o : outputs) {
                  buffer[i++] = o;
                }
                Arrays.sort(buffer);
                logger.log("Cleaning output files:");
                for (final String o : buffer) {
                  logger.log(o);
                }
                logger.log("End of files");
              }
            }

            for (String output : outputs) {
              new File(output).delete();
            }
          }

          // check if deleted source was associated with a form
          final SourceToFormMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
          final String formPath = sourceToFormMap.getState(deletedSource);
          if (formPath != null) {
            final File formFile = new File(formPath);
            if (formFile.exists()) {
              FSOperations.markDirty(context, formFile);
            }
            sourceToFormMap.remove(deletedSource);
          }
        }
      }
      if (!removedSources.isEmpty()) {
        final Map<String, Collection<String>> existing = Utils.REMOVED_SOURCES_KEY.get(context);
        if (existing != null) {
          for (Map.Entry<String, Collection<String>> entry : existing.entrySet()) {
            final Collection<String> paths = removedSources.get(entry.getKey());
            if (paths != null) {
              paths.addAll(entry.getValue());
            }
            else {
              removedSources.put(entry.getKey(), entry.getValue());
            }
          }
        }
        Utils.REMOVED_SOURCES_KEY.set(context, removedSources);
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  // return true if changed something, false otherwise
  private boolean runModuleLevelBuilders(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    boolean doneSomething = false;
    boolean rebuildFromScratchRequested = false;
    float stageCount = myTotalModuleLevelBuilderCount;
    final int modulesInChunk = chunk.getModules().size();
    int buildersPassed = 0;
    boolean nextPassRequired;
    do {
      nextPassRequired = false;
      myProjectDescriptor.fsState.beforeNextRoundStart(context, chunk);

      if (!context.isProjectRebuild()) {
        syncOutputFiles(context, chunk);
      }

      BUILDER_CATEGORY_LOOP:
      for (BuilderCategory category : BuilderCategory.values()) {
        final List<ModuleLevelBuilder> builders = myBuilderRegistry.getBuilders(category);
        if (builders.isEmpty()) {
          continue;
        }

        for (ModuleLevelBuilder builder : builders) {
          if (context.isMake()) {
            processDeletedPaths(context, chunk);
          }
          final ModuleLevelBuilder.ExitCode buildResult = builder.build(context, chunk);

          doneSomething |= (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE);

          if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
            throw new ProjectBuildException("Builder " + builder.getDescription() + " requested build stop");
          }
          context.checkCanceled();
          if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
            if (!nextPassRequired) {
              // recalculate basis
              myModulesProcessed -= (buildersPassed * modulesInChunk) / stageCount;
              stageCount += myTotalModuleLevelBuilderCount;
              myModulesProcessed += (buildersPassed * modulesInChunk) / stageCount;
            }
            nextPassRequired = true;
          }
          else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
            if (!rebuildFromScratchRequested && !context.isProjectRebuild()) {
              LOG.info("Builder " + builder.getDescription() + " requested rebuild of module chunk " + chunk.getName());
              // allow rebuild from scratch only once per chunk
              rebuildFromScratchRequested = true;
              try {
                // forcibly mark all files in the chunk dirty
                FSOperations.markDirty(context, chunk);
                // reverting to the beginning
                myModulesProcessed -= (buildersPassed * modulesInChunk) / stageCount;
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
              context.getLoggingManager().getJavaBuilderLogger().log(
                "Builder " + builder.getDescription() + " requested second chunk rebuild");
            }
          }

          buildersPassed++;
          final float fraction = updateFractionBuilderFinished(modulesInChunk / (stageCount));
          context.setDone(fraction);
        }
      }
    }
    while (nextPassRequired);

    return doneSomething;
  }

  private void runProjectLevelBuilders(CompileContext context) throws ProjectBuildException {
    for (ProjectLevelBuilder builder : myBuilderRegistry.getProjectLevelBuilders()) {
      builder.build(context);
      context.checkCanceled();
    }
  }

  private static void syncOutputFiles(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    final boolean compilingTests = context.isCompilingTests();
    try {
      final Collection<String> allOutputs = new LinkedList<String>();

      FSOperations.processFilesToRecompile(context, chunk, new FileProcessor() {
        private final Map<JpsModule, SourceToOutputMapping> storageMap = new HashMap<JpsModule, SourceToOutputMapping>();

        @Override
        public boolean apply(JpsModule module, File file, String sourceRoot) throws IOException {
          SourceToOutputMapping srcToOut = storageMap.get(module);
          if (srcToOut == null) {
            srcToOut = dataManager.getSourceToOutputMap(module.getName(), compilingTests);
            storageMap.put(module, srcToOut);
          }
          final String srcPath = FileUtil.toSystemIndependentName(file.getPath());
          final Collection<String> outputs = srcToOut.getState(srcPath);

          if (outputs != null) {
            final JavaBuilderLogger logger = context.getLoggingManager().getJavaBuilderLogger();
            for (String output : outputs) {
              if (logger.isEnabled()) {
                allOutputs.add(output);
              }
              new File(output).delete();
            }
            srcToOut.remove(srcPath);
          }
          return true;
        }
      });

      final JavaBuilderLogger logger = context.getLoggingManager().getJavaBuilderLogger();
      if (logger.isEnabled()) {
        if (context.isMake() && allOutputs.size() > 0) {
          logger.log("Cleaning output files:");
          final String[] buffer = new String[allOutputs.size()];
          int i = 0;
          for (String output : allOutputs) {
            buffer[i++] = output;
          }
          Arrays.sort(buffer);
          for (String output : buffer) {
            logger.log(output);
          }
          logger.log("End of files");
        }
      }
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  private static List<ChunkGroup> buildChunkGroups(CompileContext context, ProjectChunks chunks) {
    final List<ModuleChunk> allChunks = chunks.getChunkList();

    // building aux dependencies map
    final Map<JpsModule, Set<JpsModule>> depsMap = new HashMap<JpsModule, Set<JpsModule>>();
    final boolean compilingTests = context.isCompilingTests();
    for (JpsModule module : context.getProjectDescriptor().jpsProject.getModules()) {
      Set<JpsModule> dependent = depsMap.get(module);
      if (dependent == null) {
        dependent = ProjectPaths.getModulesWithDependentsRecursively(module, compilingTests);
        dependent.remove(module);
        depsMap.put(module, dependent);
      }
    }

    final List<ChunkGroup> groups = new ArrayList<ChunkGroup>();
    ChunkGroup currentGroup = new ChunkGroup();
    groups.add(currentGroup);
    for (ModuleChunk chunk : allChunks) {
      if (dependsOnGroup(chunk, currentGroup, depsMap)) {
        currentGroup = new ChunkGroup();
        groups.add(currentGroup);
      }
      currentGroup.addChunk(chunk);
    }
    return groups;
  }


  public static boolean dependsOnGroup(ModuleChunk chunk, ChunkGroup group, Map<JpsModule, Set<JpsModule>> depsMap) {
    for (ModuleChunk groupChunk : group.getChunks()) {
      final Set<JpsModule> groupChunkModules = groupChunk.getModules();
      for (JpsModule module : chunk.getModules()) {
        if (Utils.intersects(depsMap.get(module), groupChunkModules)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void onChunkBuildComplete(CompileContext context, @NotNull ModuleChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final BuildFSState fsState = pd.fsState;
    fsState.clearContextRoundData(context);
    fsState.clearContextChunk(context);

    if (!Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
      boolean marked = false;
      for (JpsModule module : chunk.getModules()) {
        if (context.isMake()) {
          // ensure non-incremental flag cleared
          context.clearNonIncrementalMark(module);
        }
        if (context.isProjectRebuild()) {
          fsState.markInitialScanPerformed(module.getName(), context.isCompilingTests());
        }
        final Timestamps timestamps = pd.timestamps.getStorage();
        final List<RootDescriptor> roots = pd.rootsIndex.getModuleRoots(context, module);
        for (RootDescriptor rd : roots) {
          if (context.isCompilingTests() ? rd.isTestRoot : !rd.isTestRoot) {
            marked |= fsState.markAllUpToDate(context.getScope(), rd, timestamps, context.getCompilationStartStamp());
          }
        }
      }

      if (marked) {
        context.processMessage(UptoDateFilesSavedEvent.INSTANCE);
      }
    }
  }

  private static void ensureFSStateInitialized(CompileContext context, ModuleChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final Timestamps timestamps = pd.timestamps.getStorage();
    for (JpsModule module : chunk.getModules()) {
      if (context.isProjectRebuild()) {
        FSOperations.markDirtyFiles(context, module, timestamps, true,
                                    context.isCompilingTests() ? FSOperations.DirtyMarkScope.TESTS : FSOperations.DirtyMarkScope.PRODUCTION, null);
        updateOutputRootsLayout(context, module);
      }
      else {
        if (context.isMake()) {
          if (pd.fsState.markInitialScanPerformed(module.getName(), context.isCompilingTests())) {
            initModuleFSState(context, module);
            updateOutputRootsLayout(context, module);
          }
        }
        else {
          // forced compilation mode
          if (context.getScope().isRecompilationForced(module.getName())) {
            FSOperations.markDirtyFiles(context, module, timestamps, true,
                                        context.isCompilingTests() ? FSOperations.DirtyMarkScope.TESTS : FSOperations.DirtyMarkScope.PRODUCTION,
                                        null);
            updateOutputRootsLayout(context, module);
          }
        }
      }
    }
  }

  private static void initModuleFSState(CompileContext context, JpsModule module) throws IOException {
    boolean forceMarkDirty = false;
    final File currentOutput = context.getProjectPaths().getModuleOutputDir(module, context.isCompilingTests());
    final ProjectDescriptor pd = context.getProjectDescriptor();
    if (currentOutput != null) {
      Pair<String, String> outputsPair = pd.dataManager.getOutputRootsLayout().getState(module.getName());
      if (outputsPair != null) {
        final String previousPath = context.isCompilingTests() ? outputsPair.second : outputsPair.first;
        forceMarkDirty = StringUtil.isEmpty(previousPath) || !FileUtil.filesEqual(currentOutput, new File(previousPath));
      }
      else {
        forceMarkDirty = true;
      }
    }

    final Timestamps timestamps = pd.timestamps.getStorage();
    final HashSet<File> currentFiles = new HashSet<File>();
    FSOperations.markDirtyFiles(context, module, timestamps, forceMarkDirty, context.isCompilingTests() ? FSOperations.DirtyMarkScope.TESTS : FSOperations.DirtyMarkScope.PRODUCTION, currentFiles);

    // handle deleted paths
    final BuildFSState fsState = pd.fsState;
    fsState.clearDeletedPaths(module.getName(), context.isCompilingTests());
    final SourceToOutputMapping sourceToOutputMap = pd.dataManager.getSourceToOutputMap(module.getName(), context.isCompilingTests());
    for (final Iterator<String> it = sourceToOutputMap.getKeysIterator(); it.hasNext();) {
      final String path = it.next();
      // can check if the file exists
      final File file = new File(path);
      if (!currentFiles.contains(file)) {
        fsState.registerDeleted(module.getName(), file, context.isCompilingTests(), timestamps);
      }
    }
  }

  private static void updateOutputRootsLayout(CompileContext context, JpsModule module) throws IOException {
    final File currentOutput = context.getProjectPaths().getModuleOutputDir(module, context.isCompilingTests());
    if (currentOutput == null) {
      return;
    }
    final ModuleOutputRootsLayout outputRootsLayout = context.getProjectDescriptor().dataManager.getOutputRootsLayout();
    Pair<String, String> outputsPair = outputRootsLayout.getState(module.getName());
    // update data
    final String productionPath;
    final String testPath;
    if (context.isCompilingTests()) {
      productionPath = outputsPair != null? outputsPair.first : "";
      testPath = FileUtil.toSystemIndependentName(currentOutput.getPath());
    }
    else {
      productionPath = FileUtil.toSystemIndependentName(currentOutput.getPath());
      testPath = outputsPair != null? outputsPair.second : "";
    }
    outputRootsLayout.update(module.getName(), Pair.create(productionPath, testPath));
  }

  private static class ChunkGroup {
    private final List<ModuleChunk> myChunks = new ArrayList<ModuleChunk>();

    public void addChunk(ModuleChunk chunk) {
      myChunks.add(chunk);
    }

    public List<ModuleChunk> getChunks() {
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
    return (CompileContext)Proxy.newProxyInstance(loader, new Class[] {CompileContext.class}, new InvocationHandler() {
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
        return method.invoke(delegate, args);
      }
    });
  }

}
