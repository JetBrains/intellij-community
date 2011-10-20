package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

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
    final CompileContext context = new CompileContext(
      scope, myProjectName, isMake, myMappings, myProductionChunks, myTestChunks, new MessageHandler() {

      public void processMessage(BuildMessage msg) {
        for (MessageHandler h : myMessageHandlers) {
          h.processMessage(msg);
        }
      }
    });
    try {
      cleanOutputRoots(scope, context);

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
    catch (ProjectBuildException e) {
      context.processMessage(new ProgressMessage(e.getMessage()));
    }
    finally {
      context.processMessage(new ProgressMessage("Build complete"));
      context.getBuildDataManager().close();
      final File mappingsDataFile = Paths.getMappingsStorageFile(myProjectName);
      try {
        FileUtil.createIfDoesntExist(mappingsDataFile);
        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new DeflaterOutputStream(new FileOutputStream(mappingsDataFile))));
        try {
          context.getMappings().write(writer);
        }
        finally {
          writer.close();
        }
      }
      catch (IOException e) {
        context.processMessage(new CompilerMessage(JPS_SERVER_NAME, BuildMessage.Kind.WARNING, e.getMessage()));
      }
    }
  }

  private static void cleanOutputRoots(CompileScope scope, CompileContext context) {
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
      for (BuilderCategory category : BuilderCategory.values()) {
        runBuilders(context, chunk, myBuilderRegistry.getBuilders(category));
      }
    }
    finally {
      context.clearFileCache();
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

}
