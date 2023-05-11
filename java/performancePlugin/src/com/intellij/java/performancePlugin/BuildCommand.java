package com.intellij.java.performancePlugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.task.ModuleBuildTask;
import com.intellij.task.ProjectTaskManager;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Command builds all modules in a project. There are two modes: BUILD and REBUILD
 * Syntax: %buildProject [mode]
 */
public class BuildCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "buildProject";
  private static final String REBUILD = "REBUILD";
  private static final String BUILD = "BUILD";

  public static final String SPAN_NAME = "build_compilation_duration";

  public BuildCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    String mode = extractCommandArgument(PREFIX);
    Project project = context.getProject();
    MessageBusConnection connection = project.getMessageBus().connect();
    SpanBuilder span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext());
    Ref<Span> spanRef = new Ref<>();
    Ref<Scope> scopeRef = new Ref<>();
    connection
      .subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
          if (errors > 0) {
            CompilerMessage[] errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR);
            StringBuilder errorMessage = new StringBuilder("Errors happened during build: " + errors);
            for (CompilerMessage error : errorMessages) {
              errorMessage.append("\n").append(error);
            }
            context.message(errorMessage.toString(), getLine());
            spanRef.get().setAttribute("Errors", errors);
          }
          if (warnings > 0) {
            CompilerMessage[] warningMessages = compileContext.getMessages(CompilerMessageCategory.WARNING);
            StringBuilder warningMessage = new StringBuilder("Warnings happened during build: " + warnings);
            for (CompilerMessage warning : warningMessages) {
              warningMessage.append("\n").append(warning);
            }
            context.message(warningMessage.toString(), getLine());
            spanRef.get().setAttribute("Warnings", warnings);
          }
          connection.disconnect();
        }
      });

    ApplicationManager.getApplication().invokeLater(() -> {
      ProjectTaskManager instance = ProjectTaskManager.getInstance(project);
      Promise<ProjectTaskManager.Result> promise = null;
      spanRef.set(span.startSpan());
      scopeRef.set(spanRef.get().makeCurrent());
      if (mode.equals(BUILD)) {
        promise = instance.buildAllModules();
      }
      else if (mode.equals(REBUILD)) {
        promise = instance.rebuildAllModules();
      }
      else {
        actionCallback.reject("Specified mode is neither BUILD nor REBUILD");
      }
      if (promise != null) {
        promise.onSuccess(result -> {
          spanRef.get().end();
          scopeRef.get().close();
          boolean failed = hasCompilationErrors(result);
          if (failed) {
            String errorMessage = PerformanceTestingBundle.message("command.build.finish.with.compilation.errors");
            context.message(errorMessage, getLine());
            actionCallback.reject(errorMessage);
          }
          else if (result.hasErrors() || result.isAborted() || project.isDisposed()) {
            String errorMessage;
            if (result.hasErrors()) {
              errorMessage = PerformanceTestingBundle.message("command.build.finish.with.errors");
            }
            else if (result.isAborted()) {
              errorMessage = PerformanceTestingBundle.message("command.build.aborted");
            }
            else {
              errorMessage = PerformanceTestingBundle.message("command.build.project.disposed");
            }
            context.message(errorMessage, getLine());
            actionCallback.reject(errorMessage);
          }
          else {
            context.message(PerformanceTestingBundle.message("command.build.finish"), getLine());
            actionCallback.setDone();
          }
        });
      }
    });
    return Promises.toPromise(actionCallback);
  }

  public static boolean hasCompilationErrors(@NotNull ProjectTaskManager.Result result) {
    return result.anyTaskMatches((task, state) -> task instanceof ModuleBuildTask &&
                                                  state.isFailed());
  }
}