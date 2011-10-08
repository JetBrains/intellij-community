package org.jetbrains.jps.incremental;

import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class IncProjectBuilder {

  private final Project myProject;
  private final String myProjectName;
  private final BuilderRegistry myBuilderRegistry;
  private ProjectChunks myProductionChunks;
  private ProjectChunks myTestChunks;
  private final List<MessageHandler> myMessageHandlers = new ArrayList<MessageHandler>();

  public IncProjectBuilder(Project project, String projectName, BuilderRegistry builderRegistry) {
    myProject = project;
    myProjectName = projectName;
    myBuilderRegistry = builderRegistry;
    myProductionChunks = new ProjectChunks(project, ClasspathKind.PRODUCTION_COMPILE);
    myTestChunks = new ProjectChunks(project, ClasspathKind.TEST_COMPILE);
  }

  public void addMessageHandler(MessageHandler handler) {
    myMessageHandlers.add(handler);
  }

  public void build(CompileScope scope, final boolean isMake) {
    final CompileContext context = new CompileContext(scope, myProjectName, isMake) {
      public void processMessage(BuildMessage msg) {
        for (MessageHandler h : myMessageHandlers) {
          h.processMessage(msg);
        }
      }
    };
    try {
      if (!isMake) {
        context.getBuildDataManager().clean();
      }

      for (Module module : scope.getAffectedModules()) {
        //context.processMessage(new ProgressMessage("Cleaning module " + module.getName()));
        //myProject.cleanModule(module);
      }

      runTasks(context, myBuilderRegistry.getBeforeTasks());

      context.setCompilingTests(false);
      buildChunks(context, myProductionChunks);

      context.setCompilingTests(true);
      buildChunks(context, myTestChunks);

      runTasks(context, myBuilderRegistry.getAfterTasks());
    }
    catch (ProjectBuildException e) {
      context.processMessage(new ProgressMessage(e.getMessage()));
    }
    catch (Throwable e) {
      Throwable cause = e.getCause();
      if (cause == null) {
        cause = e;
      }
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      cause.printStackTrace(new PrintStream(out));

      final StringBuilder messageText = new StringBuilder();
      messageText.append("JPS Internal error: (").append(cause.getClass().getName()).append(") ").append(e.getMessage());
      final String trace = out.toString();
      if (!trace.isEmpty()) {
        messageText.append("\n").append(trace);
      }

      context.processMessage(new CompilerMessage("JPS Server", BuildMessage.Kind.ERROR, messageText.toString()));
    }
    finally {
      context.getBuildDataManager().close();
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

        final String sourcesKind = context.isCompilingTests() ? "test" : "production";
        context.processMessage(new ProgressMessage("Compiling " + chunk.getName() + "[" + sourcesKind+"]; Compiler: " + builder.getDescription()));

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
