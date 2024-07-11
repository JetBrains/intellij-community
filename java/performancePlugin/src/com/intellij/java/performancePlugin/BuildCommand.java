// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ModuleBuildTask;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.impl.ProjectTaskManagerImpl;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.commands.OpenFileCommand;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command builds/rebuilds modules/files in a project. There are three modes: BUILD, REBUILD and RECOMPILE_FILES
 * Syntax: %buildProject BUILD - build all modules
 * Syntax: %buildProject REBUILD - rebuild all modules
 * Syntax: %buildProject BUILD [moduleName1 moduleName2] - build moduleName1 and moduleName2
 * Syntax: %buildProject REBUILD [moduleName1 moduleName2] - rebuild moduleName1 and moduleName2
 * Syntax: %buildProject RECOMPILE_FILES [fileName] - recompile fileName
 */
public class BuildCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "buildProject";
  private static final String REBUILD = "REBUILD";
  private static final String BUILD = "BUILD";
  private static final String RECOMPILE_FILES = "RECOMPILE_FILES";

  public static final String SPAN_NAME = "build_compilation_duration";

  public BuildCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    List<String> arguments = extractCommandList(PREFIX, " ");
    String mode = arguments.remove(0);
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
            context.message("Compilation FAILED.", getLine());
            CompilerMessage[] errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR);
            StringBuilder errorMessage = new StringBuilder("Errors happened during build: " + errors);
            for (CompilerMessage error : errorMessages) {
              errorMessage.append("\n").append(error);
            }
            context.message(errorMessage.toString(), getLine());
            spanRef.get().setAttribute("Errors", errors);
          }
          if (warnings > 0) {
            spanRef.get().setAttribute("Warnings", warnings);
          }
          connection.disconnect();
        }
      });

    ApplicationManager.getApplication().invokeLater(() -> {
      ProjectTaskManagerImpl.putBuildOriginator(project, this.getClass());
      ProjectTaskManager instance = ProjectTaskManager.getInstance(project);
      Promise<ProjectTaskManager.Result> promise = null;
      spanRef.set(span.startSpan());
      scopeRef.set(spanRef.get().makeCurrent());
      switch (mode) {
        case BUILD -> promise = build(getModules(arguments, project), instance);
        case REBUILD -> promise = rebuild(getModules(arguments, project), instance);
        case RECOMPILE_FILES -> promise = recompileFiles(arguments, project);
        default -> actionCallback.reject("Specified mode is neither BUILD nor REBUILD nor RECOMPILE_FILES");
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

  private static List<Module> getModules(List<String> moduleNames, Project project) {
    return moduleNames.isEmpty() ? Collections.emptyList() : Arrays.stream(ModuleManager.getInstance(project).getModules())
      .filter(module -> moduleNames.contains(module.getName()))
      .toList();
  }

  private static Promise<ProjectTaskManager.Result> build(List<Module> modules, ProjectTaskManager projectTaskManager) {
    return modules.isEmpty() ? projectTaskManager.buildAllModules() : projectTaskManager.build(modules.toArray(Module.EMPTY_ARRAY));
  }

  private static Promise<ProjectTaskManager.Result> rebuild(List<Module> modules, ProjectTaskManager projectTaskManager) {
    return modules.isEmpty() ? projectTaskManager.rebuildAllModules() : projectTaskManager.rebuild(modules.toArray(Module.EMPTY_ARRAY));
  }

  private static Promise<ProjectTaskManager.Result> recompileFiles(List<String> fileNames, Project project) {
    var files = fileNames.stream().map(fileName -> {
      var file = OpenFileCommand.findFile(fileName, project);
      if (file == null) throw new IllegalStateException("File not found : " + fileName);
      return file;
    }).toArray(VirtualFile[]::new);

    if (files.length == 0) {
      throw new IllegalStateException("Enter at least one file");
    }

    return ProjectTaskManager.getInstance(project).compile(files);
  }
}