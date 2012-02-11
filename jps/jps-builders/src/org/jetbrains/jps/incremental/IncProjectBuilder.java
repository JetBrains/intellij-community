package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.MappingFailedException;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.incremental.java.ExternalJavacDescriptor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToFormMapping;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.IncProjectBuilder");

  public static final String COMPILE_SERVER_NAME = "COMPILE SERVER";

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
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

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry, CanceledStatus cs) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myCancelStatus = cs;
    myProductionChunks = new ProjectChunks(pd.project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(pd.project, ClasspathKind.TEST_COMPILE);
    myTotalModulesWork = (float)pd.rootsIndex.getTotalModuleCount() * 2;  /* multiply by 2 to reflect production and test sources */
    myTotalModuleLevelBuilderCount = builderRegistry.getModuleLevelBuilderCount();
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void build(CompileScope scope, final boolean isMake, final boolean isProjectRebuild) {
    CompileContext context = null;
    try {
      try {
        context = createContext(scope, isMake, isProjectRebuild);
        runBuild(context);
      }
      catch (ProjectBuildException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof PersistentEnumerator.CorruptedException || cause instanceof MappingFailedException || cause instanceof IOException) {
          // force rebuild
          myMessageDispatcher.processMessage(new CompilerMessage(
            COMPILE_SERVER_NAME, BuildMessage.Kind.INFO,
            "Internal caches are corrupted or have outdated format, forcing project rebuild: " +
            e.getMessage())
          );
          flushContext(context);
          context = createContext(new AllProjectScope(scope.getProject(), Collections.<Artifact>emptySet(), true), false, true);
          runBuild(context);
        }
        else {
          throw e;
        }
      }
    }
    catch (ProjectBuildException e) {
      final Throwable cause = e.getCause();
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
    finally {
      flushContext(context);
    }
  }

  private static void flushContext(CompileContext context) {
    if (context != null) {
      context.getTimestampStorage().force();
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

  private void runBuild(CompileContext context) throws ProjectBuildException {
    context.setDone(0.0f);

    if (context.isProjectRebuild()) {
      cleanOutputRoots(context);
    }

    context.processMessage(new ProgressMessage("Running 'before' tasks"));
    runTasks(context, myBuilderRegistry.getBeforeTasks());

    context.setCompilingTests(false);
    context.processMessage(new ProgressMessage("Building production sources"));
    buildChunks(context, myProductionChunks);

    context.setCompilingTests(true);
    context.processMessage(new ProgressMessage("Building test sources"));
    buildChunks(context, myTestChunks);

    context.processMessage(new ProgressMessage("Building project"));
    runProjectLevelBuilders(context);

    context.processMessage(new ProgressMessage("Running 'after' tasks"));
    runTasks(context, myBuilderRegistry.getAfterTasks());

    context.processMessage(new ProgressMessage("Finished, saving caches..."));
  }

  private CompileContext createContext(CompileScope scope, boolean isMake, final boolean isProjectRebuild) throws ProjectBuildException {
    final TimestampStorage tsStorage = myProjectDescriptor.timestamps.getStorage();
    final FSState fsState = myProjectDescriptor.fsState;
    final ModuleRootsIndex rootsIndex = myProjectDescriptor.rootsIndex;
    final BuildDataManager dataManager = myProjectDescriptor.dataManager;
    return new CompileContext(scope, isMake, isProjectRebuild, myProductionChunks, myTestChunks, fsState, dataManager, tsStorage,
                              myMessageDispatcher, rootsIndex, myCancelStatus);
  }

  private void cleanOutputRoots(CompileContext context) throws ProjectBuildException {
    // whole project is affected
    try {
      myProjectDescriptor.timestamps.clean();
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
    myProjectDescriptor.fsState.onRebuild();

    final Collection<Module> modulesToClean = context.getProject().getModules().values();
    final Set<File> rootsToDelete = new HashSet<File>();
    final Set<File> allSourceRoots = new HashSet<File>();

    for (Module module : modulesToClean) {
      final File out = context.getProjectPaths().getModuleOutputDir(module, false);
      if (out != null) {
        rootsToDelete.add(out);
      }
      final File testOut = context.getProjectPaths().getModuleOutputDir(module, true);
      if (testOut != null) {
        rootsToDelete.add(testOut);
      }
      final List<RootDescriptor> moduleRoots = context.getModuleRoots(module);
      for (RootDescriptor d : moduleRoots) {
        allSourceRoots.add(d.root);
      }
    }

    // check that output and source roots are not overlapping
    final List<File> filesToDelete = new ArrayList<File>();
    for (File outputRoot : rootsToDelete) {
      context.checkCanceled();
      boolean okToDelete = true;
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
      }
    }

    context.processMessage(new ProgressMessage("Cleaning output directories..."));
    FileUtil.asyncDelete(filesToDelete);
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

  private void buildChunk(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    try {
      context.ensureFSStateInitialized(chunk);
      if (context.isMake()) {
        // cleanup outputs
        final Set<String> allChunkRemovedSources = new HashSet<String>();
        final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();

        for (Module module : chunk.getModules()) {
          final Collection<String> deletedPaths = myProjectDescriptor.fsState.getDeletedPaths(module, context.isCompilingTests());
          allChunkRemovedSources.addAll(deletedPaths);

          final String moduleName = module.getName().toLowerCase(Locale.US);
          final SourceToOutputMapping sourceToOutputStorage =
            context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests());
          // actually delete outputs associated with removed paths
          for (String deletedSource : deletedPaths) {
            // deleting outputs corresponding to non-existing source
            final Collection<String> outputs = sourceToOutputStorage.getState(deletedSource);
            
            if (LOG.isDebugEnabled()) {
              if (outputs.size() > 0) {
                final String[] buffer = new String[outputs.size()];
                int i = 0;
                for (final String o : outputs) {
                  buffer[i++] = o;
                }
                Arrays.sort(buffer);
                LOG.info("Cleaning output files:");
                for(final String o : buffer) {
                  LOG.info(o);
                }
                LOG.info("End of files");
              }
            }
            
            if (outputs != null) {
              for (String output : outputs) {
                FileUtil.delete(new File(output));
              }
              sourceToOutputStorage.remove(deletedSource);
            }
            // check if deleted source was associated with a form
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
        Paths.CHUNK_REMOVED_SOURCES_KEY.set(context, allChunkRemovedSources);
        for (Module module : chunk.getModules()) {
          myProjectDescriptor.fsState.clearDeletedPaths(module, context.isCompilingTests());
        }
      }

      context.onChunkBuildStart(chunk);

      runModuleLevelBuilders(context, chunk);
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
          Paths.CHUNK_REMOVED_SOURCES_KEY.set(context, null);
        }
      }
    }
  }

  private void runModuleLevelBuilders(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    boolean rebuildFromScratchRequested = false;
    float stageCount = myTotalModuleLevelBuilderCount;
    CHUNK_BUILD_START:
    for (BuilderCategory category : BuilderCategory.values()) {
      final List<ModuleLevelBuilder> builders = myBuilderRegistry.getBuilders(category);
      if (builders.isEmpty()) {
        continue;
      }

      final int modulesInChunk = chunk.getModules().size();
      int buildersPassed = 0;

      boolean nextPassRequired;
      do {
        nextPassRequired = false;
        context.beforeCompileRound(chunk);

        if (!context.isProjectRebuild()) {
          syncOutputFiles(context, chunk);
        }

        for (ModuleLevelBuilder builder : builders) {
          final ModuleLevelBuilder.ExitCode buildResult = builder.build(context, chunk);

          if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
            throw new ProjectBuildException("Builder " + builder.getDescription() + " requested build stop");
          }
          context.checkCanceled();
          if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
            if (!nextPassRequired) {
              // recalculate basis
              myModulesProcessed -= (buildersPassed * modulesInChunk) / stageCount;
              stageCount += builders.size();
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
                break CHUNK_BUILD_START;
              }
              catch (Exception e) {
                throw new ProjectBuildException(e);
              }
            }
            else {
              LOG.info("Builder " + builder.getDescription() + " requested second chunk rebuild");
            }
          }

          buildersPassed++;
          final float fraction = updateFractionBuilderFinished(modulesInChunk / (stageCount));
          context.setDone(fraction);
        }
      }
      while (nextPassRequired);

      context.afterCompileRound();
    }
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
            srcToOut = dataManager.getSourceToOutputMap(module.getName().toLowerCase(Locale.US), compilingTests);
            storageMap.put(module, srcToOut);
          }
          final String srcPath = FileUtil.toSystemIndependentName(file.getPath());
          final Collection<String> outputs = srcToOut.getState(srcPath);

          if (outputs != null) {
            for (String output : outputs) {
              if (LOG.isDebugEnabled()) {
                allOutputs.add(output);
              }
              FileUtil.delete(new File(output));
            }
            srcToOut.remove(srcPath);
          }
          return true;
        }
      });

      if (LOG.isDebugEnabled()) {
        if (context.isMake() && allOutputs.size() > 0) {
          LOG.info("Cleaning output files:");
          final String[] buffer = new String[allOutputs.size()];
          int i = 0;
          for (String output : allOutputs) {
            buffer[i++] = output;
          }
          Arrays.sort(buffer);
          for (String output : buffer) {
            LOG.info(output);
          }
          LOG.info("End of files");
        }
      }
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }
}
