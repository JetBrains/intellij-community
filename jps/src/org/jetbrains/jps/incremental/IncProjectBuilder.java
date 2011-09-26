package org.jetbrains.jps.incremental;

import org.jetbrains.jps.ClasspathKind;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectChunks;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {

  private final Project myProject;
  private final BuilderRegistry myBuilderRegistry;
  private ProjectChunks myProductionChunks;
  private ProjectChunks myTestChunks;

  public IncProjectBuilder(Project project, BuilderRegistry builderRegistry) {
    myProject = project;
    myBuilderRegistry = builderRegistry;
    myProductionChunks = new ProjectChunks(project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(project, ClasspathKind.TEST_COMPILE);
  }

  public void build(CompileScope scope) {
    final CompileContext context = new CompileContext(scope);
    try {
      runTasks(context, myBuilderRegistry.getBeforeTasks());

      buildChunks(context, myProductionChunks);

      buildChunks(context, myTestChunks);

      runTasks(context, myBuilderRegistry.getAfterTasks());
    }
    catch (ProjectBuildException e) {
      e.printStackTrace(); // todo
    }
  }

  private void runTasks(CompileContext context, final List<BuildTask> tasks) throws ProjectBuildException {
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
    for (BuilderCategory category : BuilderCategory.values()) {
      runBuilders(context, chunk, myBuilderRegistry.getBuilders(category));
    }
  }

  private void runBuilders(CompileContext context, ModuleChunk chunk, List<Builder> builders) throws ProjectBuildException {
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
