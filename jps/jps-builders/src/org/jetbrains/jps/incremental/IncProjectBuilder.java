package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OutputToSourceMapping;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {
  public static final String JPS_SERVER_NAME = "JPS BUILD";

  private final String myProjectName;
  private final BuilderRegistry myBuilderRegistry;
  private ProjectChunks myProductionChunks;
  private ProjectChunks myTestChunks;
  private final List<MessageHandler> myMessageHandlers = new ArrayList<MessageHandler>();
  private final Mappings myMappings;

  public IncProjectBuilder(String projectName,
                           Project project,
                           final Mappings mappings,
                           BuilderRegistry builderRegistry) {
    myProjectName = projectName;
    myBuilderRegistry = builderRegistry;
    myProductionChunks = new ProjectChunks(project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(project, ClasspathKind.TEST_COMPILE);
    myMappings = mappings;
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void build(CompileScope scope, final boolean isMake) {

    final CompileContext context = createContext(scope, isMake);
    try {
      try {
        runBuild(context);
      }
      catch (ProjectBuildException e) {
        if (e.getCause() instanceof PersistentEnumerator.CorruptedException) {
          // force rebuild
          context.processMessage(new CompilerMessage(
            JPS_SERVER_NAME, BuildMessage.Kind.INFO,
            "Internal caches are corrupted or have outdated format, forcing project rebuild: " + e.getMessage())
          );
          runBuild(createContext(new CompileScope(scope.getProject()), false));
        }
        else {
          throw e;
        }
      }

    }
    catch (ProjectBuildException e) {
      final Throwable cause = e.getCause();
      if (cause == null) {
        context.processMessage(new ProgressMessage(e.getMessage()));
      }
      else {
        context.processMessage(new CompilerMessage(JPS_SERVER_NAME, cause));
      }
    }
    finally {
      context.getBuildDataManager().close();
    }
  }

  private void runBuild(CompileContext context) throws ProjectBuildException {
    cleanOutputRoots(context);

    context.processMessage(new ProgressMessage("Running 'before' tasks"));
    runTasks(context, myBuilderRegistry.getBeforeTasks());

    context.setCompilingTests(false);
    context.processMessage(new ProgressMessage("Building production sources"));
    buildChunks(context, myProductionChunks);

    context.setCompilingTests(true);
    context.processMessage(new ProgressMessage("Building test sources"));
    buildChunks(context, myTestChunks);

    context.processMessage(new ProgressMessage("Running 'after' tasks"));
    runTasks(context, myBuilderRegistry.getAfterTasks());
  }

  private CompileContext createContext(CompileScope scope, boolean isMake) {
    return new CompileContext(
      scope, myProjectName, isMake, myMappings, myProductionChunks, myTestChunks, new MessageHandler() {

      public void processMessage(BuildMessage msg) {
        for (MessageHandler h : myMessageHandlers) {
          h.processMessage(msg);
        }
      }
    });
  }

  private static void cleanOutputRoots(CompileContext context) {
    final CompileScope scope = context.getScope();
    final Collection<Module> allProjectModules = scope.getProject().getModules().values();
    final Collection<Module> modulesToClean = new HashSet<Module>();

    if (context.isMake()) {
      for (Module module : scope.getAffectedModules()) {
        if (context.isDirty(module)) {
          modulesToClean.add(module);
        }
      }
    }
    else {
      modulesToClean.addAll(scope.getAffectedModules());

      final Set<Module> allModules = new HashSet<Module>(allProjectModules);
      allModules.removeAll(scope.getAffectedModules());
      if (allModules.isEmpty()) {
        // whole project is affected
        context.getBuildDataManager().clean();
      }
    }

    if (!modulesToClean.isEmpty()) {
      final Set<File> toDelete = new HashSet<File>();
      final Set<File> allSourceRoots = new HashSet<File>();
      for (Module module : modulesToClean) {
        final File out = context.getProjectPaths().getModuleOutputDir(module, false);
        if (out != null) {
          toDelete.add(out);
        }
        final File testOut = context.getProjectPaths().getModuleOutputDir(module, true);
        if (testOut != null) {
          toDelete.add(testOut);
        }
      }
      for (Module module : allProjectModules) {
        for (Object root : module.getSourceRoots()) {
          allSourceRoots.add(new File((String)root));
        }
        for (Object root : module.getTestRoots()) {
          allSourceRoots.add(new File((String)root));
        }
      }
      // check that output and source roots are not overlapping
      for (File outputRoot : toDelete) {
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
          context.processMessage(new ProgressMessage("Cleaning " + outputRoot.getPath()));
          FileUtil.delete(outputRoot);
        }
        else {
          context.processMessage(new CompilerMessage(JPS_SERVER_NAME, BuildMessage.Kind.WARNING, "Output path " + outputRoot.getPath() + " intersects with a source root. The output cannot be cleaned."));
        }
      }
    }
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
    }
  }

  private void buildChunk(CompileContext context, ModuleChunk chunk) throws ProjectBuildException{
    try {
         // TODO: check how the output-source storage is filled and!
      if (context.isMake()) {
        // cleanup outputs
        final HashSet<File> allChunkSources = new HashSet<File>();
        context.processFiles(chunk, new FilesCollector(allChunkSources, FilesCollector.ALL_FILES));

        final OutputToSourceMapping storage = context.getBuildDataManager().getOutputToSourceStorage();
        final HashSet<File> allChunkRemovedSources = new HashSet<File>();
        for (Module module : chunk.getModules()) {
          final File moduleOutput = context.getProjectPaths().getModuleOutputDir(module, context.isCompilingTests());
          if (moduleOutput != null && moduleOutput.exists()) {
            deleteOutputsOfRemovedSources(moduleOutput, storage, allChunkSources, allChunkRemovedSources);
          }
        }

        Paths.CHUNK_REMOVED_SOURCES_KEY.set(context, allChunkRemovedSources);
      }

      for (BuilderCategory category : BuilderCategory.values()) {
        runBuilders(context, chunk, myBuilderRegistry.getBuilders(category));
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      for (BuilderCategory category : BuilderCategory.values()) {
        for (Builder builder : myBuilderRegistry.getBuilders(category)) {
          builder.cleanupResources(context, chunk);
        }
      }
      context.onChunkBuildComplete(chunk);
      Paths.CHUNK_REMOVED_SOURCES_KEY.set(context, null);
    }
  }

  private static void runBuilders(CompileContext context, ModuleChunk chunk, List<Builder> builders) throws ProjectBuildException {
    boolean nextPassRequired;
    do {
      nextPassRequired = false;
      for (Builder builder : builders) {
        final Builder.ExitCode buildResult = builder.build(context, chunk);
        if (buildResult == Builder.ExitCode.ABORT) {
          throw new ProjectBuildException("Builder " + builder.getDescription() + " requested build stop");
        }
        if (buildResult == Builder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
          nextPassRequired = true;
        }
      }
    }
    while (nextPassRequired);
  }

  private static void deleteOutputsOfRemovedSources(File file, final OutputToSourceMapping outputToSourceStorage, Set<File> allChunkSources, Set<File> removedSources) throws Exception {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          deleteOutputsOfRemovedSources(child, outputToSourceStorage, allChunkSources, removedSources);
        }
      }
    }
    else {
      final String outPath = file.getPath();
      final String srcPath = outputToSourceStorage.getState(outPath);
      if (srcPath != null) {
        // if we know about the association
        final File outputSource = new File(srcPath);
        if (!allChunkSources.contains(outputSource)) {
          removedSources.add(outputSource);
          FileUtil.delete(file);
          outputToSourceStorage.remove(outPath);
        }
      }
    }
  }

}
