package org.jetbrains.jps.incremental;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.MappingFailedException;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.api.SharedThreadPool;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.java.ExternalJavacDescriptor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToFormMapping;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.*;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.IncProjectBuilder");

  public static final String COMPILE_SERVER_NAME = "COMPILE SERVER";
  private static final String CLASSPATH_INDEX_FINE_NAME = "classpath.index";
  private static final boolean GENERATE_CLASSPATH_INDEX = "true".equals(System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION));

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
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

  private float myModulesProcessed = 0.0f;
  private final float myTotalModulesWork;
  private final int myTotalModuleLevelBuilderCount;
  private final List<Future> myAsyncTasks = new ArrayList<Future>();
  private final Timestamps myTimestamps;

  public IncProjectBuilder(ProjectDescriptor pd,
                           BuilderRegistry builderRegistry,
                           final Timestamps timestamps,
                           Map<String, String> builderParams,
                           CanceledStatus cs) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myBuilderParams = builderParams;
    myCancelStatus = cs;
    myProductionChunks = new ProjectChunks(pd.project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(pd.project, ClasspathKind.TEST_COMPILE);
    myTotalModulesWork = (float)pd.rootsIndex.getTotalModuleCount() * 2;  /* multiply by 2 to reflect production and test sources */
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
    myTimestamps = timestamps;
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void build(CompileScope scope, final boolean isMake, final boolean isProjectRebuild, boolean forceCleanCaches)
    throws RebuildRequestedException {
    final LowMemoryWatcher memWatcher = LowMemoryWatcher.register(new Forceable() {
      @Override
      public boolean isDirty() {
        return true; // always perform flush when not enough memory
      }

      @Override
      public void force() {
        myProjectDescriptor.dataManager.flush(false);
        myTimestamps.force();
      }
    });
    CompileContext context = null;
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
          COMPILE_SERVER_NAME, BuildMessage.Kind.INFO,
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
          myMessageDispatcher.processMessage(new CompilerMessage(COMPILE_SERVER_NAME, cause));
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
      context.getTimestamps().force();
      context.getDataManager().flush(false);
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
    cleanupJavacNameTable();
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
      LOG.info(e);
    }
  }

  private float updateFractionBuilderFinished(final float delta) {
    myModulesProcessed += delta;
    return myModulesProcessed / myTotalModulesWork;
  }

  private void runBuild(CompileContext context, boolean forceCleanCaches) throws ProjectBuildException {
    context.setDone(0.0f);

    LOG.info("Building project '" +
             context.getProject().getProjectName() +
             "'; isRebuild:" +
             context.isProjectRebuild() +
             "; isMake:" +
             context.isMake());

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

    final JavaBuilderLogger logger = context.getLoggingManager().getJavaBuilderLogger();

    if (logger.isEnabled()) {
      final File flag = new File(Utils.getSystemRoot() + File.separator + "dumpSnapshot");

      if (flag.exists()) {
        final Mappings mappings = myProjectDescriptor.dataManager.getMappings();
        final String fileName =
          Utils.getSystemRoot() + File.separator + "snapshot-" + new SimpleDateFormat("dd-MM-yy(hh:mm:ss)").format(new Date()) + ".log";

        try {
          final PrintStream stream = new PrintStream(fileName);

          stream.println("Mappings:");
          mappings.toStream(stream);
          stream.println("End Of Mappings");

          stream.close();
        }
        catch (FileNotFoundException e) {
          e.printStackTrace();
        }
      }
    }

    context.processMessage(new ProgressMessage("Finished, saving caches..."));
  }

  private CompileContext createContext(CompileScope scope, boolean isMake, final boolean isProjectRebuild) throws ProjectBuildException {
    return new CompileContext(
      scope, myProjectDescriptor, isMake, isProjectRebuild, myProductionChunks, myTestChunks, myMessageDispatcher,
      myBuilderParams, myTimestamps, myCancelStatus
    );
  }

  private void cleanOutputRoots(CompileContext context) throws ProjectBuildException {
    // whole project is affected
    final boolean shouldClear = context.getProject().getCompilerConfiguration().isClearOutputDirectoryOnRebuild();
    try {
      if (shouldClear) {
        clearOutputs(context);
      }
      else {
        for (Module module : context.getProject().getModules().values()) {
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
      context.getTimestamps().clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning timestamps storage", e);
    }
    try {
      context.getDataManager().clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning compiler storages", e);
    }
    myProjectDescriptor.fsState.clearAll();
  }

  private static void clearOutputFiles(CompileContext context, final String moduleName, boolean forTests) throws IOException {
    final SourceToOutputMapping map = context.getDataManager().getSourceToOutputMap(moduleName, forTests);
    for (String srcPath : map.getKeys()) {
      final Collection<String> outs = map.getState(srcPath);
      if (outs != null) {
        for (String out : outs) {
          new File(out).delete();
        }
      }
    }
  }

  private static void clearOutputs(CompileContext context) throws ProjectBuildException, IOException {
    final Collection<Module> modulesToClean = context.getProject().getModules().values();
    final Map<File, Set<Pair<String, Boolean>>> rootsToDelete =
      new HashMap<File, Set<Pair<String, Boolean>>>(); // map: outputRoot-> setOfPairs([module, isTest])
    final Set<File> allSourceRoots = new HashSet<File>();

    for (Module module : modulesToClean) {
      final File out = context.getProjectPaths().getModuleOutputDir(module, false);
      if (out != null) {
        appendRootInfo(rootsToDelete, out, module, false);
      }
      final File testOut = context.getProjectPaths().getModuleOutputDir(module, true);
      if (testOut != null) {
        appendRootInfo(rootsToDelete, testOut, module, true);
      }
      final List<RootDescriptor> moduleRoots = context.getModuleRoots(module);
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
      if (PathUtil.isUnder(allSourceRoots, outputRoot)) {
        okToDelete = false;
      }
      else {
        final Set<File> _outRoot = Collections.singleton(outputRoot);
        for (File srcRoot : allSourceRoots) {
          if (PathUtil.isUnder(_outRoot, srcRoot)) {
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
        context.processMessage(new CompilerMessage(COMPILE_SERVER_NAME, BuildMessage.Kind.WARNING, "Output path " +
                                                                                                   outputRoot.getPath() +
                                                                                                   " intersects with a source root. The output cannot be cleaned."));
        // clean only those files we are aware of
        for (Pair<String, Boolean> info : entry.getValue()) {
          clearOutputFiles(context, info.first, info.second);
        }
      }
    }

    context.processMessage(new ProgressMessage("Cleaning output directories..."));
    FileUtil.asyncDelete(filesToDelete);
  }

  private static void appendRootInfo(Map<File, Set<Pair<String, Boolean>>> rootsToDelete, File out, Module module, boolean isTest) {
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

  private void buildChunks(CompileContext context, ProjectChunks chunks) throws ProjectBuildException {
    final CompileScope scope = context.getScope();
    for (ModuleChunk chunk : chunks.getChunkList()) {
      if (scope.isAffected(chunk)) {
        buildChunk(context, chunk);
      }
      else {
        final float fraction = updateFractionBuilderFinished(chunk.getModules().size());
        context.setDone(fraction);
      }
    }
  }

  private void buildChunk(CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    boolean doneSomething = false;
    try {
      context.ensureFSStateInitialized(chunk);
      if (context.isMake()) {
        processDeletedPaths(context, chunk);
        doneSomething |= context.hasRemovedSources();
      }

      context.onChunkBuildStart(chunk);

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
          context.onChunkBuildComplete(chunk);
        }
        catch (Exception e) {
          throw new ProjectBuildException(e);
        }
        finally {
          Utils.CHUNK_REMOVED_SOURCES_KEY.set(context, null);
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
    for (Module module : chunk.getModules()) {
      final String out = forTests ? module.getTestOutputPath() : module.getOutputPath();
      if (out != null) {
        outputPaths.add(new File(out));
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
      final Set<String> allChunkRemovedSources = new HashSet<String>();

      for (Module module : chunk.getModules()) {
        final Collection<String> deletedPaths = myProjectDescriptor.fsState.getDeletedPaths(module.getName(), context.isCompilingTests());
        if (deletedPaths.isEmpty()) {
          continue;
        }
        allChunkRemovedSources.addAll(deletedPaths);

        final SourceToOutputMapping sourceToOutputStorage =
          context.getDataManager().getSourceToOutputMap(module.getName(), context.isCompilingTests());
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
            sourceToOutputStorage.remove(deletedSource);
          }

          // check if deleted source was associated with a form
          final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();
          final String formPath = sourceToFormMap.getState(deletedSource);
          if (formPath != null) {
            final File formFile = new File(formPath);
            if (formFile.exists()) {
              context.markDirty(formFile);
            }
            sourceToFormMap.remove(deletedSource);
          }
        }
      }
      if (!allChunkRemovedSources.isEmpty()) {
        final Set<String> currentData = Utils.CHUNK_REMOVED_SOURCES_KEY.get(context);
        if (currentData != null) {
          allChunkRemovedSources.addAll(currentData);
        }
        Utils.CHUNK_REMOVED_SOURCES_KEY.set(context, allChunkRemovedSources);
        for (Module module : chunk.getModules()) {
          myProjectDescriptor.fsState.clearDeletedPaths(module.getName(), context.isCompilingTests());
        }
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
      context.beforeCompileRound(chunk);

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
              // allow rebuild from scratch only once per chunk
              rebuildFromScratchRequested = true;
              try {
                // forcibly mark all files in the chunk dirty
                context.markDirty(chunk);
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
    final BuildDataManager dataManager = context.getDataManager();
    final boolean compilingTests = context.isCompilingTests();
    try {
      final Collection<String> allOutputs = new LinkedList<String>();

      context.processFilesToRecompile(chunk, new FileProcessor() {
        private final Map<Module, SourceToOutputMapping> storageMap = new HashMap<Module, SourceToOutputMapping>();

        @Override
        public boolean apply(Module module, File file, String sourceRoot) throws IOException {
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
}
