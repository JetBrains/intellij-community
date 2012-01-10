package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToFormMapping;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {
  public static final String JPS_SERVER_NAME = "JPS BUILD";

  private final ProjectDescriptor myProjectDescriptor;
  private final BuilderRegistry myBuilderRegistry;
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

  public IncProjectBuilder(ProjectDescriptor pd, BuilderRegistry builderRegistry) {
    myProjectDescriptor = pd;
    myBuilderRegistry = builderRegistry;
    myProductionChunks = new ProjectChunks(pd.project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(pd.project, ClasspathKind.TEST_COMPILE);
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
        if (e.getCause() instanceof PersistentEnumerator.CorruptedException) {
          // force rebuild
          myMessageDispatcher.processMessage(new CompilerMessage(
            JPS_SERVER_NAME, BuildMessage.Kind.INFO,
            "Internal caches are corrupted or have outdated format, forcing project rebuild: " + e.getMessage())
          );
          context = createContext(new CompileScope(scope.getProject()), false, true);
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
        myMessageDispatcher.processMessage(new ProgressMessage(e.getMessage()));
      }
      else {
        myMessageDispatcher.processMessage(new CompilerMessage(JPS_SERVER_NAME, cause));
      }
    }
    finally {
      if (context != null) {
        context.getDataManager().close();
      }
      cleanupJavacNameTable();
    }
  }

  private static void cleanupJavacNameTable() {
    try {
      final Field freelistField = Class.forName("com.sun.tools.javac.util.Name$Table").getDeclaredField("freelist");
      freelistField.setAccessible(true);
      freelistField.set(null, com.sun.tools.javac.util.List.nil());
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void runBuild(CompileContext context) throws ProjectBuildException {
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

    context.processMessage(new ProgressMessage("Running 'after' tasks"));
    runTasks(context, myBuilderRegistry.getAfterTasks());
  }

  private CompileContext createContext(CompileScope scope, boolean isMake, final boolean isProjectRebuild) throws ProjectBuildException {
    final String projectName = myProjectDescriptor.projectName;
    final TimestampStorage tsStorage = myProjectDescriptor.timestamps.getStorage();
    final FSState fsState = myProjectDescriptor.fsState;
    return new CompileContext(
      projectName, scope, isMake, isProjectRebuild, myProductionChunks, myTestChunks, fsState, tsStorage, myMessageDispatcher
    );
  }

  private static void cleanOutputRoots(CompileContext context) throws ProjectBuildException {
    // whole project is affected
    try {
      context.getDataManager().clean();
    }
    catch (IOException e) {
      throw new ProjectBuildException("Error cleaning compiler storages", e);
    }

    final Collection<Module> modulesToClean = context.getProject().getModules().values();
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
      final List<RootDescriptor> moduleRoots = context.getModuleRoots(module);
      for (RootDescriptor d : moduleRoots) {
        allSourceRoots.add(d.root);
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
        // do not delete output root itself to avoid lots of unnecessary "roots_changed" events in IDEA
        final File[] children = outputRoot.listFiles();
        if (children != null) {
          for (File child : children) {
            FileUtil.delete(child);
          }
        }
      }
      else {
        context.processMessage(new CompilerMessage(JPS_SERVER_NAME, BuildMessage.Kind.WARNING, "Output path " + outputRoot.getPath() + " intersects with a source root. The output cannot be cleaned."));
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
      context.ensureFSStateInitialized(chunk);
      if (context.isMake()) {
        // cleanup outputs
        final Set<String> allChunkRemovedSources = new HashSet<String>();
        final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();

        for (Module module : chunk.getModules()) {
          final Collection<String> deletedPaths = myProjectDescriptor.fsState.getDeletedPaths(module, context.isCompilingTests());
          allChunkRemovedSources.addAll(deletedPaths);

          final String moduleName = module.getName().toLowerCase(Locale.US);
          final SourceToOutputMapping sourceToOutputStorage = context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests());
          // actually delete outputs associated with removed paths
          for (String deletedSource : deletedPaths) {
            // deleting outputs corresponding to non-existing source
            final Collection<String> outputs = sourceToOutputStorage.getState(deletedSource);
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

      for (BuilderCategory category : BuilderCategory.values()) {
        runBuilders(context, chunk, category);
      }
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
          for (Builder builder : myBuilderRegistry.getBuilders(category)) {
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

  private void runBuilders(CompileContext context, ModuleChunk chunk, BuilderCategory category) throws ProjectBuildException {
    final List<Builder> builders = myBuilderRegistry.getBuilders(category);
    if (builders.isEmpty()) {
      return;
    }
    boolean nextPassRequired;
    do {
      nextPassRequired = false;
      context.beforeNextCompileRound(chunk);
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
}
