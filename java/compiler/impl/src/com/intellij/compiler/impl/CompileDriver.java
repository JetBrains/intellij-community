// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.CommonBundle;
import com.intellij.build.BuildContentManager;
import com.intellij.compiler.*;
import com.intellij.compiler.progress.CompilerMessagesService;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.DefaultMessageHandler;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompilerUtil;
import com.intellij.packaging.impl.compiler.ArtifactsCompiler;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.tracing.Tracer;
import com.intellij.util.Chunk;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public final class CompileDriver {
  private static final Logger LOG = Logger.getInstance(CompileDriver.class);

  private static final Key<Boolean> COMPILATION_STARTED_AUTOMATICALLY = Key.create("compilation_started_automatically");
  private static final Key<ExitStatus> COMPILE_SERVER_BUILD_STATUS = Key.create("COMPILE_SERVER_BUILD_STATUS");
  private static final long ONE_MINUTE_MS = 60L * 1000L;

  private final Project myProject;
  private final Map<Module, String> myModuleOutputPaths = new HashMap<>();
  private final Map<Module, String> myModuleTestOutputPaths = new HashMap<>();

  public CompileDriver(Project project) {
    myProject = project;
  }

  @SuppressWarnings({"deprecation", "unused"})
  public void setCompilerFilter(@SuppressWarnings("unused") CompilerFilter compilerFilter) {
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, new ProjectCompileScope(myProject));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    make(scope, false, callback);
  }

  public void make(CompileScope scope, boolean withModalProgress, CompileStatusNotification callback) {
    if (validateCompilerConfiguration(scope)) {
      startup(scope, false, false, withModalProgress, callback, null);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.create(myProject));
    }
  }

  public boolean isUpToDate(@NotNull CompileScope scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation started");
    }

    final CompilerTask task = new CompilerTask(myProject, JavaCompilerBundle.message("classes.up.to.date.check"), true, false, false,
                                               isCompilationStartedAutomatically(scope));
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, task, scope, true, false);

    final Ref<ExitStatus> result = new Ref<>();

    Runnable compileWork = () -> {
      ProgressIndicator indicator = compileContext.getProgressIndicator();
      if (indicator.isCanceled() || myProject.isDisposed()) {
        return;
      }

      final BuildManager buildManager = BuildManager.getInstance();
      try {
        buildManager.postponeBackgroundTasks();
        buildManager.cancelAutoMakeTasks(myProject);
        TaskFuture<?> future = compileInExternalProcess(compileContext, true);
        if (future != null) {
          while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
            if (indicator.isCanceled()) {
              future.cancel(false);
            }
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      finally {
        ExitStatus exitStatus = COMPILE_SERVER_BUILD_STATUS.get(compileContext);
        task.setEndCompilationStamp(exitStatus, System.currentTimeMillis());
        result.set(exitStatus);
        buildManager.allowBackgroundTasks(false);
        if (!myProject.isDisposed()) {
          CompilerCacheManager.getInstance(myProject).flushCaches();
        }
      }
    };

    ProgressIndicatorProvider indicatorProvider = ProgressIndicatorProvider.getInstance();
    if (!EventQueue.isDispatchThread() && indicatorProvider.getProgressIndicator() != null) {
      // if called from background process on pooled thread, run synchronously
      task.run(compileWork, null, indicatorProvider.getProgressIndicator());
    }
    else {
      task.start(compileWork, null);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation finished");
    }

    return ExitStatus.UP_TO_DATE.equals(result.get());
  }

  public void compile(CompileScope scope, CompileStatusNotification callback) {
    if (validateCompilerConfiguration(scope)) {
      startup(scope, false, true, callback, null);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.create(myProject));
    }
  }

  private void doRebuild(CompileStatusNotification callback, final CompileScope compileScope) {
    if (validateCompilerConfiguration(compileScope)) {
      startup(compileScope, true, false, callback, null);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.create(myProject));
    }
  }

  public static void setCompilationStartedAutomatically(CompileScope scope) {
    //todo[nik] pass this option as a parameter to compile/make methods instead
    scope.putUserData(COMPILATION_STARTED_AUTOMATICALLY, true);
  }

  private static boolean isCompilationStartedAutomatically(CompileScope scope) {
    return Boolean.TRUE.equals(scope.getUserData(COMPILATION_STARTED_AUTOMATICALLY));
  }

  private List<TargetTypeBuildScope> getBuildScopes(@NotNull CompileContextImpl compileContext, CompileScope scope, Collection<String> paths) {
    List<TargetTypeBuildScope> scopes = new ArrayList<>();
    final boolean forceBuild = !compileContext.isMake();
    List<TargetTypeBuildScope> explicitScopes = CompileScopeUtil.getBaseScopeForExternalBuild(scope);
    if (explicitScopes != null) {
      scopes.addAll(explicitScopes);
    }
    else if (!compileContext.isRebuild() && (!paths.isEmpty() || !CompileScopeUtil.allProjectModulesAffected(compileContext))) {
      CompileScopeUtil.addScopesForSourceSets(scope.getAffectedSourceSets(), scope.getAffectedUnloadedModules(), scopes, forceBuild);
    }
    else {
      final Collection<ModuleSourceSet> sourceSets = scope.getAffectedSourceSets();
      boolean includeTests = sourceSets.isEmpty();
      for (ModuleSourceSet sourceSet : sourceSets) {
        if (sourceSet.getType().isTest()) {
          includeTests = true;
          break;
        }
      }
      if (includeTests) {
        scopes.addAll(CmdlineProtoUtil.createAllModulesScopes(forceBuild));
      }
      else {
        scopes.add(CmdlineProtoUtil.createAllModulesProductionScope(forceBuild));
      }
    }
    if (paths.isEmpty()) {
      scopes = mergeScopesFromProviders(scope, scopes, forceBuild);
    }
    return scopes;
  }

  private List<TargetTypeBuildScope> mergeScopesFromProviders(CompileScope scope,
                                                              List<TargetTypeBuildScope> scopes,
                                                              boolean forceBuild) {
    for (BuildTargetScopeProvider provider : BuildTargetScopeProvider.EP_NAME.getExtensions()) {
      List<TargetTypeBuildScope> providerScopes = ReadAction.compute(
        () -> myProject.isDisposed() ? Collections.emptyList()
                                     : provider.getBuildTargetScopes(scope, myProject, forceBuild));
      scopes = CompileScopeUtil.mergeScopes(scopes, providerScopes);
    }
    return scopes;
  }

  @Nullable
  private TaskFuture<?> compileInExternalProcess(@NotNull final CompileContextImpl compileContext, final boolean onlyCheckUpToDate) {
    final CompileScope scope = compileContext.getCompileScope();
    final Collection<String> paths = CompileScopeUtil.fetchFiles(compileContext);
    List<TargetTypeBuildScope> scopes = getBuildScopes(compileContext, scope, paths);

    // need to pass scope's user data to server
    final Map<String, String> builderParams;
    if (onlyCheckUpToDate) {
      builderParams = new HashMap<>();
    }
    else {
      Map<Key<?>, Object> exported = scope.exportUserData();
      if (!exported.isEmpty()) {
        builderParams = new HashMap<>();
        for (Map.Entry<Key<?>, Object> entry : exported.entrySet()) {
          final String _key = entry.getKey().toString();
          final String _value = entry.getValue().toString();
          builderParams.put(_key, _value);
        }
      }
      else {
        builderParams = new HashMap<>();
      }
    }
    if (!scope.getAffectedUnloadedModules().isEmpty()) {
      builderParams.put(BuildParametersKeys.LOAD_UNLOADED_MODULES, Boolean.TRUE.toString());
    }

    Map<String, List<Artifact>> outputToArtifact =
      ArtifactCompilerUtil.containsArtifacts(scopes) ? ArtifactCompilerUtil.createOutputToArtifactMap(myProject) : null;
    return BuildManager.getInstance()
      .scheduleBuild(myProject, compileContext.isRebuild(), compileContext.isMake(), onlyCheckUpToDate, scopes, paths, builderParams,
                     new DefaultMessageHandler(myProject) {
                       @Override
                       public void sessionTerminated(@NotNull UUID sessionId) {
                         if (compileContext.shouldUpdateProblemsView()) {
                           ProblemsView view = myProject.getServiceIfCreated(ProblemsView.class);
                           if (view != null) {
                             view.clearProgress();
                             view.clearOldMessages(compileContext.getCompileScope(), compileContext.getSessionId());
                           }
                         }
                       }

                       @Override
                       public void handleFailure(@NotNull UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
                         //noinspection HardCodedStringLiteral
                         compileContext
                           .addMessage(CompilerMessageCategory.ERROR, failure.hasDescription() ? failure.getDescription() : "", null, -1,
                                       -1);
                         final String trace = failure.hasStacktrace() ? failure.getStacktrace() : null;
                         if (trace != null) {
                           LOG.info(trace);
                         }
                         compileContext.putUserData(COMPILE_SERVER_BUILD_STATUS, ExitStatus.ERRORS);
                       }

                       @Override
                       protected void handleCompileMessage(UUID sessionId,
                                                           CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
                         final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind = message.getKind();
                         //System.out.println(compilerMessage.getText());
                         //noinspection HardCodedStringLiteral
                         final String messageText = message.getText();
                         if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.PROGRESS) {
                           final ProgressIndicator indicator = compileContext.getProgressIndicator();
                           indicator.setText(messageText);
                           if (message.hasDone()) {
                             indicator.setFraction(message.getDone());
                           }
                         }
                         else {
                           final CompilerMessageCategory category = convertToCategory(kind, CompilerMessageCategory.INFORMATION);

                           String sourceFilePath = message.hasSourceFilePath() ? message.getSourceFilePath() : null;
                           if (sourceFilePath != null) {
                             sourceFilePath = FileUtil.toSystemIndependentName(sourceFilePath);
                           }
                           final long line = message.hasLine() ? message.getLine() : -1;
                           final long column = message.hasColumn() ? message.getColumn() : -1;
                           final String srcUrl =
                             sourceFilePath != null ? VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, sourceFilePath) : null;
                           compileContext
                             .addMessage(category, messageText, srcUrl, (int)line, (int)column, null, message.getModuleNamesList());
                           if (compileContext.shouldUpdateProblemsView() &&
                               kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.JPS_INFO) {
                             // treat JPS_INFO messages in a special way: add them as info messages to the problems view
                             final Project project = compileContext.getProject();
                             ProblemsView.getInstance(project).addMessage(
                               new CompilerMessageImpl(project, category, messageText),
                               compileContext.getSessionId()
                             );
                           }
                         }
                       }

                       @Override
                       protected void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event) {
                         final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type eventType = event.getEventType();
                         switch (eventType) {
                           case FILES_GENERATED:
                             final List<CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile> generated =
                               event.getGeneratedFilesList();
                             CompilationStatusListener publisher =
                               myProject.isDisposed() ? null : myProject.getMessageBus().syncPublisher(CompilerTopics.COMPILATION_STATUS);
                             Set<String> writtenArtifactOutputPaths =
                               outputToArtifact != null ? CollectionFactory.createFilePathSet() : null;
                             for (CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile generatedFile : generated) {
                               final String root = FileUtil.toSystemIndependentName(generatedFile.getOutputRoot());
                               final String relativePath = FileUtil.toSystemIndependentName(generatedFile.getRelativePath());
                               if (publisher != null) {
                                 publisher.fileGenerated(root, relativePath);
                               }
                               if (outputToArtifact != null) {
                                 Collection<Artifact> artifacts = outputToArtifact.get(root);
                                 if (artifacts != null && !artifacts.isEmpty()) {
                                   writtenArtifactOutputPaths
                                     .add(FileUtil.toSystemDependentName(DeploymentUtil.appendToPath(root, relativePath)));
                                 }
                               }
                             }
                             if (writtenArtifactOutputPaths != null && !writtenArtifactOutputPaths.isEmpty()) {
                               ArtifactsCompiler.addWrittenPaths(compileContext, writtenArtifactOutputPaths);
                             }
                             break;

                           case BUILD_COMPLETED:
                             ExitStatus status = ExitStatus.SUCCESS;
                             if (event.hasCompletionStatus()) {
                               final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status completionStatus =
                                 event.getCompletionStatus();
                               switch (completionStatus) {
                                 case CANCELED:
                                   status = ExitStatus.CANCELLED;
                                   break;
                                 case ERRORS:
                                   status = ExitStatus.ERRORS;
                                   break;
                                 case SUCCESS:
                                   break;
                                 case UP_TO_DATE:
                                   status = ExitStatus.UP_TO_DATE;
                                   break;
                               }
                             }
                             compileContext.putUserDataIfAbsent(COMPILE_SERVER_BUILD_STATUS, status);
                             break;

                           case CUSTOM_BUILDER_MESSAGE:
                             if (event.hasCustomBuilderMessage()) {
                               final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.CustomBuilderMessage message =
                                 event.getCustomBuilderMessage();
                               if (GlobalOptions.JPS_SYSTEM_BUILDER_ID.equals(message.getBuilderId()) &&
                                   GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID.equals(message.getMessageType())) {
                                 //noinspection HardCodedStringLiteral
                                 final String text = message.getMessageText();
                                 if (!StringUtil.isEmpty(text)) {
                                   compileContext.addMessage(CompilerMessageCategory.INFORMATION, text, null, -1, -1);
                                 }
                               }
                             }
                             break;
                         }
                       }

                       @Override
                       public @NotNull ProgressIndicator getProgressIndicator() {
                         return compileContext.getProgressIndicator();
                       }
                     });
  }

  private void startup(final CompileScope scope, final boolean isRebuild, final boolean forceCompile,
                       final CompileStatusNotification callback, final CompilerMessage message) {
    startup(scope, isRebuild, forceCompile, false, callback, message);
  }

  private void startup(final CompileScope scope,
                       final boolean isRebuild,
                       final boolean forceCompile,
                       boolean withModalProgress, final CompileStatusNotification callback,
                       final CompilerMessage message) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    final String name = JavaCompilerBundle
      .message(
        isRebuild ? "compiler.content.name.rebuild" : forceCompile ? "compiler.content.name.recompile" : "compiler.content.name.make");
    Tracer.Span span = Tracer.start(name + " preparation");
    final CompilerTask compileTask = new CompilerTask(
      myProject, name, isUnitTestMode, !withModalProgress, true, isCompilationStartedAutomatically(scope), withModalProgress
    );

    StatusBar.Info.set("", myProject, "Compiler");
    // ensure the project model seen by build process is up-to-date
    myProject.save();
    if (!isUnitTestMode) {
      ApplicationManager.getApplication().saveSettings();
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final CompileContextImpl compileContext = new CompileContextImpl(myProject, compileTask, scope, !isRebuild && !forceCompile, isRebuild);
    span.complete();
    final Runnable compileWork = () -> {
      Tracer.Span compileWorkSpan = Tracer.start("compileWork");
      final ProgressIndicator indicator = compileContext.getProgressIndicator();
      if (indicator.isCanceled() || myProject.isDisposed()) {
        if (callback != null) {
          callback.finished(true, 0, 0, compileContext);
        }
        return;
      }
      CompilerCacheManager compilerCacheManager = CompilerCacheManager.getInstance(myProject);
      final BuildManager buildManager = BuildManager.getInstance();
      try {
        buildManager.postponeBackgroundTasks();
        buildManager.cancelAutoMakeTasks(myProject);
        LOG.info("COMPILATION STARTED (BUILD PROCESS)");
        if (message != null) {
          compileContext.addMessage(message);
        }
        if (isRebuild) {
          CompilerUtil.runInContext(compileContext, JavaCompilerBundle.message("progress.text.clearing.build.system.data"),
                                    (ThrowableRunnable<Throwable>)() -> compilerCacheManager
                                      .clearCaches(compileContext));
        }
        final boolean beforeTasksOk = executeCompileTasks(compileContext, true);

        final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
        if (!beforeTasksOk || errorCount > 0) {
          COMPILE_SERVER_BUILD_STATUS.set(compileContext, errorCount > 0 ? ExitStatus.ERRORS : ExitStatus.CANCELLED);
          return;
        }

        TaskFuture<?> future = compileInExternalProcess(compileContext, false);
        if (future != null) {
          Tracer.Span compileInExternalProcessSpan = Tracer.start("compile in external process");
          while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
            if (indicator.isCanceled()) {
              future.cancel(false);
            }
          }
          compileInExternalProcessSpan.complete();
          if (!executeCompileTasks(compileContext, false)) {
            COMPILE_SERVER_BUILD_STATUS.set(compileContext, ExitStatus.CANCELLED);
          }
          if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            COMPILE_SERVER_BUILD_STATUS.set(compileContext, ExitStatus.ERRORS);
          }
        }
      }
      catch (ProcessCanceledException ignored) {
        compileContext.putUserDataIfAbsent(COMPILE_SERVER_BUILD_STATUS, ExitStatus.CANCELLED);
      }
      catch (Throwable e) {
        LOG.error(e); // todo
      }
      finally {
        compileWorkSpan.complete();
        buildManager.allowBackgroundTasks(
          true // reset state on explicit build to compensate possibly unbalanced postpone/allow calls (e.g. via BatchFileChangeListener.start/stop)
        );
        Tracer.Span flushCompilerCaches = Tracer.start("flush compiler caches");
        compilerCacheManager.flushCaches();
        flushCompilerCaches.complete();

        final long duration = notifyCompilationCompleted(compileContext, callback, COMPILE_SERVER_BUILD_STATUS.get(compileContext));
        CompilerUtil.logDuration(
          "\tCOMPILATION FINISHED (BUILD PROCESS); Errors: " +
          compileContext.getMessageCount(CompilerMessageCategory.ERROR) +
          "; warnings: " +
          compileContext.getMessageCount(CompilerMessageCategory.WARNING),
          duration
        );

        if (SystemProperties.getBooleanProperty("idea.is.integration.test", false)) {
          String logPath = PathManager.getLogPath();
          Path perfMetrics = Paths.get(logPath).resolve("performance-metrics").resolve("buildMetrics.json");
          try {
            FileUtil.writeToFile(perfMetrics.toFile(), "{\n\t\"build_errors\" : " +
                                                       compileContext.getMessageCount(CompilerMessageCategory.ERROR) + "," +
                                                       "\n\t\"build_warnings\" : " +
                                                       compileContext.getMessageCount(CompilerMessageCategory.WARNING) + "," +
                                                       "\n\t\"build_compilation_duration\" : " +
                                                       duration +
                                                       "\n}");
          }
          catch (IOException ex) {
            LOG.info("Could not create json file with the build performance metrics.");
          }
        }
      }
    };

    compileTask.start(compileWork, () -> {
      if (isRebuild) {
        final int rv = Messages.showOkCancelDialog(
          myProject, JavaCompilerBundle.message("you.are.about.to.rebuild.the.whole.project"),
          JavaCompilerBundle.message("confirm.project.rebuild"),
          CommonBundle.message("button.build"), JavaCompilerBundle.message("button.rebuild"), Messages.getQuestionIcon()
        );
        if (rv == Messages.OK /*yes, please, do run make*/) {
          startup(scope, false, false, callback, null);
          return;
        }
      }
      startup(scope, isRebuild, forceCompile, callback, message);
    });
  }

  @Nullable
  @TestOnly
  public static ExitStatus getExternalBuildExitStatus(CompileContext context) {
    return context.getUserData(COMPILE_SERVER_BUILD_STATUS);
  }

  /**
   * @noinspection SSBasedInspection
   */
  private long notifyCompilationCompleted(final CompileContextImpl compileContext,
                                          final CompileStatusNotification callback,
                                          final ExitStatus _status) {
    long endCompilationStamp = System.currentTimeMillis();
    compileContext.getBuildSession().setEndCompilationStamp(_status, endCompilationStamp);
    final long duration = endCompilationStamp - compileContext.getStartCompilationStamp();
    if (!myProject.isDisposed()) {
      // refresh on output roots is required in order for the order enumerator to see all roots via VFS
      final Module[] affectedModules = compileContext.getCompileScope().getAffectedModules();

      if (_status != ExitStatus.UP_TO_DATE && _status != ExitStatus.CANCELLED) {
        // have to refresh in case of errors too, because run configuration may be set to ignore errors
        Collection<String> affectedRoots = ContainerUtil.newHashSet(CompilerPaths.getOutputPaths(affectedModules));
        if (!affectedRoots.isEmpty()) {
          ProgressIndicator indicator = compileContext.getProgressIndicator();
          indicator.setText(JavaCompilerBundle.message("synchronizing.output.directories"));
          CompilerUtil.refreshOutputRoots(affectedRoots);
          indicator.setText("");
        }
      }
    }
    SwingUtilities.invokeLater(() -> {
      int errorCount = 0;
      int warningCount = 0;
      try {
        errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
        warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING);
      }
      finally {
        if (callback != null) {
          callback.finished(_status == ExitStatus.CANCELLED, errorCount, warningCount, compileContext);
        }
      }

      if (!myProject.isDisposed()) {
        final String statusMessage = createStatusMessage(_status, warningCount, errorCount, duration);
        final MessageType messageType = errorCount > 0 ? MessageType.ERROR : warningCount > 0 ? MessageType.WARNING : MessageType.INFO;
        if (duration > ONE_MINUTE_MS && CompilerWorkspaceConfiguration.getInstance(myProject).DISPLAY_NOTIFICATION_POPUP) {
          String toolWindowId = useBuildToolWindow() ? BuildContentManager.TOOL_WINDOW_ID : ToolWindowId.MESSAGES_WINDOW;
          ToolWindowManager.getInstance(myProject).notifyByBalloon(toolWindowId, messageType, statusMessage);
        }

        String wrappedMessage = _status == ExitStatus.UP_TO_DATE ? statusMessage : HtmlChunk.link("#", statusMessage).toString();
        Notification notification = CompilerManager.NOTIFICATION_GROUP.createNotification(wrappedMessage, messageType.toNotificationType())
          .setListener(new BuildToolWindowActivationListener(compileContext))
          .setImportant(false);
        compileContext.getBuildSession().registerCloseAction(notification::expire);
        notification.notify(myProject);

        if (_status != ExitStatus.UP_TO_DATE && compileContext.getMessageCount(null) > 0) {
          final String msg = DateFormatUtil.formatDateTime(new Date()) + " - " + statusMessage;
          compileContext.addMessage(CompilerMessageCategory.INFORMATION, msg, null, -1, -1);
        }
      }
    });
    return duration;
  }

  private static @Nls String createStatusMessage(final ExitStatus status, final int warningCount, final int errorCount, long duration) {
    String message;
    if (status == ExitStatus.CANCELLED) {
      message = JavaCompilerBundle.message("status.compilation.aborted");
    }
    else if (status == ExitStatus.UP_TO_DATE) {
      message = JavaCompilerBundle.message("status.all.up.to.date");
    }
    else {
      String durationString = NlsMessages.formatDurationApproximate(duration);
      if (status == ExitStatus.SUCCESS) {
        message = warningCount > 0
                  ? JavaCompilerBundle.message("status.compilation.completed.successfully.with.warnings", warningCount, durationString)
                  : JavaCompilerBundle.message("status.compilation.completed.successfully", durationString);
      }
      else {
        message = JavaCompilerBundle.message("status.compilation.completed.successfully.with.warnings.and.errors",
                                             errorCount, warningCount, durationString);
      }
    }
    return message;
  }

  // [mike] performance optimization - this method is accessed > 15,000 times in Aurora
  private String getModuleOutputPath(Module module, boolean inTestSourceContent) {
    Map<Module, String> map = inTestSourceContent ? myModuleTestOutputPaths : myModuleOutputPaths;
    return map.computeIfAbsent(module, k -> CompilerPaths.getModuleOutputPath(module, inTestSourceContent));
  }

  public void executeCompileTask(final CompileTask task,
                                 final CompileScope scope,
                                 final @NlsContexts.TabTitle String contentName,
                                 final Runnable onTaskFinished) {
    final CompilerTask progressManagerTask =
      new CompilerTask(myProject, contentName, false, false, true, isCompilationStartedAutomatically(scope));
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, progressManagerTask, scope, false, false);

    FileDocumentManager.getInstance().saveAllDocuments();

    progressManagerTask.start(() -> {
      try {
        task.execute(compileContext);
      }
      catch (ProcessCanceledException ex) {
        // suppressed
      }
      finally {
        if (onTaskFinished != null) {
          onTaskFinished.run();
        }
      }
    }, null);
  }

  private boolean executeCompileTasks(@NotNull final CompileContext context, final boolean beforeTasks) {
    if (myProject.isDisposed()) {
      return false;
    }
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    progressIndicator.pushState();
    try {
      List<CompileTask> tasks = beforeTasks ? manager.getBeforeTasks() : manager.getAfterTaskList();
      if (tasks.size() > 0) {
        progressIndicator.setText(
          JavaCompilerBundle.message(beforeTasks ? "progress.executing.precompile.tasks" : "progress.executing.postcompile.tasks"));
        for (CompileTask task : tasks) {
          try {
            if (!task.execute(context)) {
              return false;
            }
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable t) {
            LOG.error("Error executing task", t);
            context
              .addMessage(CompilerMessageCategory.INFORMATION, JavaCompilerBundle.message("error.task.0.execution.failed", task.toString()),
                          null, -1, -1);
          }
        }
      }
    }
    finally {
      progressIndicator.popState();
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      if (statusBar != null) {
        statusBar.setInfo("");
      }
    }
    return true;
  }

  private boolean validateCompilerConfiguration(@NotNull final CompileScope scope) {
    try {
      final Module[] scopeModules = scope.getAffectedModules();
      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      List<Module> modulesWithSources = ContainerUtil.filter(scopeModules, module -> {
        if (!compilerManager.isValidationEnabled(module)) return false;
        final boolean hasSources = hasSources(module, JavaSourceRootType.SOURCE);
        final boolean hasTestSources = hasSources(module, JavaSourceRootType.TEST_SOURCE);
        if (!hasSources && !hasTestSources) {
          // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
          // todo still there may be problems with this approach if some generated files are attributed by this module
          return false;
        }
        return true;
      });

      if (!validateJdks(modulesWithSources, true)) return false;
      if (!validateOutputs(modulesWithSources)) return false;
      if (!validateCyclicDependencies(scopeModules)) return false;
      return true;
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    catch (Throwable e) {
      LOG.error(e);
      return false;
    }
  }

  private boolean validateJdks(@NotNull List<Module> scopeModules, boolean runUnknownSdkCheck) {
    final List<String> modulesWithoutJdkAssigned = new ArrayList<>();
    boolean projectSdkNotSpecified = false;
    for (final Module module : scopeModules) {
      final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
      if (jdk != null) continue;
      projectSdkNotSpecified |= ModuleRootManager.getInstance(module).isSdkInherited();
      modulesWithoutJdkAssigned.add(module.getName());
    }

    if (runUnknownSdkCheck) {
      var result = CompilerDriverUnknownSdkTracker
        .getInstance(myProject)
        .fixSdkSettings(projectSdkNotSpecified, scopeModules, formatModulesList(modulesWithoutJdkAssigned));

      if (result == CompilerDriverUnknownSdkTracker.Outcome.STOP_COMPILE) {
        return false;
      }

      //we do not trust the CompilerDriverUnknownSdkTracker, to extra check has to be done anyway
      return validateJdks(scopeModules, false);
    }
    else {
      if (modulesWithoutJdkAssigned.isEmpty()) return true;
      showNotSpecifiedError("error.jdk.not.specified", projectSdkNotSpecified, modulesWithoutJdkAssigned,
                            JavaCompilerBundle.message("modules.classpath.title"));
      return false;
    }
  }

  private boolean validateOutputs(@NotNull List<Module> scopeModules) {
    final List<String> modulesWithoutOutputPathSpecified = new ArrayList<>();
    boolean projectOutputNotSpecified = false;
    for (final Module module : scopeModules) {
      final String outputPath = getModuleOutputPath(module, false);
      final String testsOutputPath = getModuleOutputPath(module, true);
      if (outputPath == null && testsOutputPath == null) {
        CompilerModuleExtension compilerExtension = CompilerModuleExtension.getInstance(module);
        projectOutputNotSpecified |= compilerExtension != null && compilerExtension.isCompilerOutputPathInherited();
        modulesWithoutOutputPathSpecified.add(module.getName());
      }
      else {
        if (outputPath == null) {
          if (hasSources(module, JavaSourceRootType.SOURCE)) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
        if (testsOutputPath == null) {
          if (hasSources(module, JavaSourceRootType.TEST_SOURCE)) {
            modulesWithoutOutputPathSpecified.add(module.getName());
          }
        }
      }
    }

    if (modulesWithoutOutputPathSpecified.isEmpty()) return true;

    showNotSpecifiedError("error.output.not.specified", projectOutputNotSpecified, modulesWithoutOutputPathSpecified,
                          DefaultModuleConfigurationEditorFactory.getInstance().getOutputEditorDisplayName());
    return false;
  }

  private boolean validateCyclicDependencies(Module[] scopeModules) {
    final List<Chunk<ModuleSourceSet>> chunks = ModuleCompilerUtil.getCyclicDependencies(myProject, Arrays.asList(scopeModules));
    for (final Chunk<ModuleSourceSet> chunk : chunks) {
      final Set<ModuleSourceSet> sourceSets = chunk.getNodes();
      if (sourceSets.size() <= 1) {
        continue; // no need to check one-module chunks
      }
      Sdk jdk = null;
      LanguageLevel languageLevel = null;
      for (final ModuleSourceSet sourceSet : sourceSets) {
        Module module = sourceSet.getModule();
        final Sdk moduleJdk = ModuleRootManager.getInstance(module).getSdk();
        if (jdk == null) {
          jdk = moduleJdk;
        }
        else {
          if (!jdk.equals(moduleJdk)) {
            showCyclicModulesErrorNotification("error.chunk.modules.must.have.same.jdk", ModuleSourceSet.getModules(sourceSets));
            return false;
          }
        }

        LanguageLevel moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
        if (languageLevel == null) {
          languageLevel = moduleLanguageLevel;
        }
        else {
          if (!languageLevel.equals(moduleLanguageLevel)) {
            showCyclicModulesErrorNotification("error.chunk.modules.must.have.same.language.level", ModuleSourceSet.getModules(sourceSets));
            return false;
          }
        }
      }
    }
    return true;
  }

  private void showCyclicModulesErrorNotification(@PropertyKey(resourceBundle = JavaCompilerBundle.BUNDLE) @NotNull String messageId,
                                                  @NotNull Set<? extends Module> modulesInChunk) {
    Module firstModule = ContainerUtil.getFirstItem(modulesInChunk);
    LOG.assertTrue(firstModule != null);
    CompileDriverNotifications.getInstance(myProject)
      .createCannotStartNotification()
      .withContent(JavaCompilerBundle.message(messageId, getModulesString(modulesInChunk)))
      .withOpenSettingsAction(firstModule.getName(), null)
      .showNotification();
  }

  private static String getModulesString(Collection<? extends Module> modulesInChunk) {
    return StringUtil.join(modulesInChunk, module -> "\"" + module.getName() + "\"", "\n");
  }

  private static boolean hasSources(Module module, final JavaSourceRootType rootType) {
    return !ModuleRootManager.getInstance(module).getSourceRoots(rootType).isEmpty();
  }

  private void showNotSpecifiedError(@PropertyKey(resourceBundle = JavaCompilerBundle.BUNDLE) @NonNls String resourceId,
                                     boolean notSpecifiedValueInheritedFromProject,
                                     List<String> modules,
                                     String editorNameToSelect) {
    String nameToSelect = notSpecifiedValueInheritedFromProject ? null : ContainerUtil.getFirstItem(modules);
    final String message = JavaCompilerBundle.message(resourceId, modules.size(), formatModulesList(modules));

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(message);
    }

    CompileDriverNotifications.getInstance(myProject)
      .createCannotStartNotification()
      .withContent(message)
      .withOpenSettingsAction(nameToSelect, editorNameToSelect)
      .showNotification();
  }

  @NotNull
  private static String formatModulesList(@NotNull List<String> modules) {
    final int maxModulesToShow = 10;
    List<String> actualNamesToInclude = new ArrayList<>(ContainerUtil.getFirstItems(modules, maxModulesToShow));
    if (modules.size() > maxModulesToShow) {
      actualNamesToInclude.add(JavaCompilerBundle.message("error.jdk.module.names.overflow.element.ellipsis"));
    }

    return NlsMessages.formatNarrowAndList(actualNamesToInclude);
  }

  public static CompilerMessageCategory convertToCategory(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind,
                                                          CompilerMessageCategory defaultCategory) {
    switch (kind) {
      case ERROR:
      case INTERNAL_BUILDER_ERROR:
        return CompilerMessageCategory.ERROR;
      case WARNING:
        return CompilerMessageCategory.WARNING;
      case INFO:
      case JPS_INFO:
      case OTHER:
        return CompilerMessageCategory.INFORMATION;
      default:
        return defaultCategory;
    }
  }

  private static final class BuildToolWindowActivationListener extends NotificationListener.Adapter {
    private final WeakReference<Project> myProjectRef;
    private final Object myContentId;

    BuildToolWindowActivationListener(CompileContextImpl compileContext) {
      myProjectRef = new WeakReference<>(compileContext.getProject());
      myContentId = compileContext.getBuildSession().getContentId();
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      Project project = myProjectRef.get();
      boolean useBuildToolwindow = useBuildToolWindow();
      String toolWindowId = useBuildToolwindow ? BuildContentManager.TOOL_WINDOW_ID : ToolWindowId.MESSAGES_WINDOW;
      if (project != null && !project.isDisposed() &&
          (useBuildToolwindow || CompilerMessagesService.showCompilerContent(project, myContentId))) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        if (toolWindow != null) {
          toolWindow.activate(null, false);
        }
      }
      else {
        notification.expire();
      }
    }
  }

  private static boolean useBuildToolWindow() {
    return SystemProperties.getBooleanProperty("ide.jps.use.build.tool.window", true);
  }
}
