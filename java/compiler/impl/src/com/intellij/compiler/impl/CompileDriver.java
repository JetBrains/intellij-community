/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.compiler.impl;

import com.intellij.CommonBundle;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.ModuleSourceSet;
import com.intellij.compiler.ProblemsView;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.DefaultMessageHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompilerUtil;
import com.intellij.packaging.impl.compiler.ArtifactsCompiler;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Chunk;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.DateFormatUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.TaskFuture;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public class CompileDriver {

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileDriver");

  private final Project myProject;

  private final Map<Module, String> myModuleOutputPaths = new HashMap<>();
  private final Map<Module, String> myModuleTestOutputPaths = new HashMap<>();

  private static final Key<Boolean> COMPILATION_STARTED_AUTOMATICALLY = Key.create("compilation_started_automatically");

  private CompilerFilter myCompilerFilter = CompilerFilter.ALL;

  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

  public CompileDriver(Project project) {
    myProject = project;
  }

  public void setCompilerFilter(CompilerFilter compilerFilter) {
    myCompilerFilter = compilerFilter == null? CompilerFilter.ALL : compilerFilter;
  }

  public void rebuild(CompileStatusNotification callback) {
    doRebuild(callback, null, new ProjectCompileScope(myProject));
  }

  public void make(CompileScope scope, CompileStatusNotification callback) {
    if (validateCompilerConfiguration(scope)) {
      startup(scope, false, false, callback, null);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
    }
  }

  public boolean isUpToDate(CompileScope scope) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("isUpToDate operation started");
    }

    final CompilerTask task = new CompilerTask(myProject, "Classes up-to-date check", true, false, false, isCompilationStartedAutomatically(scope));
    final CompileContextImpl compileContext = new CompileContextImpl(myProject, task, scope, true, false);

    final Ref<ExitStatus> result = new Ref<>();

    task.start(() -> {
      final ProgressIndicator indicator = compileContext.getProgressIndicator();
      if (indicator.isCanceled() || myProject.isDisposed()) {
        return;
      }
      try {
        final TaskFuture future = compileInExternalProcess(compileContext, true);
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
        result.set(COMPILE_SERVER_BUILD_STATUS.get(compileContext));
        if (!myProject.isDisposed()) {
          CompilerCacheManager.getInstance(myProject).flushCaches();
        }
      }
    }, null);

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
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
    }
  }

  private void doRebuild(CompileStatusNotification callback, CompilerMessage message, final CompileScope compileScope) {
    if (validateCompilerConfiguration(compileScope)) {
      startup(compileScope, true, false, callback, message);
    }
    else {
      callback.finished(true, 0, 0, DummyCompileContext.getInstance());
    }
  }

  public static void setCompilationStartedAutomatically(CompileScope scope) {
    //todo[nik] pass this option as a parameter to compile/make methods instead
    scope.putUserData(COMPILATION_STARTED_AUTOMATICALLY, Boolean.TRUE);
  }

  private static boolean isCompilationStartedAutomatically(CompileScope scope) {
    return Boolean.TRUE.equals(scope.getUserData(COMPILATION_STARTED_AUTOMATICALLY));
  }

  @Nullable
  private TaskFuture compileInExternalProcess(final @NotNull CompileContextImpl compileContext, final boolean onlyCheckUpToDate)
    throws Exception {
    final CompileScope scope = compileContext.getCompileScope();
    final Collection<String> paths = CompileScopeUtil.fetchFiles(compileContext);
    List<TargetTypeBuildScope> scopes = new ArrayList<>();
    final boolean forceBuild = !compileContext.isMake();
    List<TargetTypeBuildScope> explicitScopes = CompileScopeUtil.getBaseScopeForExternalBuild(scope);
    if (explicitScopes != null) {
      scopes.addAll(explicitScopes);
    }
    else if (!compileContext.isRebuild() && !CompileScopeUtil.allProjectModulesAffected(compileContext)) {
      CompileScopeUtil.addScopesForModules(Arrays.asList(scope.getAffectedModules()), scopes, forceBuild);
    }
    else {
      scopes.addAll(CmdlineProtoUtil.createAllModulesScopes(forceBuild));
    }
    if (paths.isEmpty()) {
      for (BuildTargetScopeProvider provider : BuildTargetScopeProvider.EP_NAME.getExtensions()) {
        scopes = CompileScopeUtil.mergeScopes(scopes, provider.getBuildTargetScopes(scope, myCompilerFilter, myProject, forceBuild));
      }
    }

    // need to pass scope's user data to server
    final Map<String, String> builderParams;
    if (onlyCheckUpToDate) {
      builderParams = Collections.emptyMap();
    }
    else {
      final Map<Key, Object> exported = scope.exportUserData();
      if (!exported.isEmpty()) {
        builderParams = new HashMap<>();
        for (Map.Entry<Key, Object> entry : exported.entrySet()) {
          final String _key = entry.getKey().toString();
          final String _value = entry.getValue().toString();
          builderParams.put(_key, _value);
        }
      }
      else {
        builderParams = Collections.emptyMap();
      }
    }

    final MessageBus messageBus = myProject.getMessageBus();
    final MultiMap<String, Artifact> outputToArtifact = ArtifactCompilerUtil.containsArtifacts(scopes) ? ArtifactCompilerUtil.createOutputToArtifactMap(myProject) : null;
    final BuildManager buildManager = BuildManager.getInstance();
    buildManager.cancelAutoMakeTasks(myProject);
    return buildManager.scheduleBuild(myProject, compileContext.isRebuild(), compileContext.isMake(), onlyCheckUpToDate, scopes, paths, builderParams, new DefaultMessageHandler(myProject) {

      @Override
      public void buildStarted(UUID sessionId) {
      }

      @Override
      public void sessionTerminated(final UUID sessionId) {
        if (compileContext.shouldUpdateProblemsView()) {
          final ProblemsView view = ProblemsView.SERVICE.getInstance(myProject);
          view.clearProgress();
          view.clearOldMessages(compileContext.getCompileScope(), compileContext.getSessionId());
        }
      }

      @Override
      public void handleFailure(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, failure.hasDescription()? failure.getDescription() : "", null, -1, -1);
        final String trace = failure.hasStacktrace()? failure.getStacktrace() : null;
        if (trace != null) {
          LOG.info(trace);
        }
        compileContext.putUserData(COMPILE_SERVER_BUILD_STATUS, ExitStatus.ERRORS);
      }

      @Override
      protected void handleCompileMessage(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
        final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind = message.getKind();
        //System.out.println(compilerMessage.getText());
        final String messageText = message.getText();
        if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.PROGRESS) {
          final ProgressIndicator indicator = compileContext.getProgressIndicator();
          indicator.setText(messageText);
          if (message.hasDone()) {
            indicator.setFraction(message.getDone());
          }
        }
        else {
          final CompilerMessageCategory category = kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.ERROR ? CompilerMessageCategory.ERROR
            : kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.WARNING ? CompilerMessageCategory.WARNING : CompilerMessageCategory.INFORMATION;

          String sourceFilePath = message.hasSourceFilePath() ? message.getSourceFilePath() : null;
          if (sourceFilePath != null) {
            sourceFilePath = FileUtil.toSystemIndependentName(sourceFilePath);
          }
          final long line = message.hasLine() ? message.getLine() : -1;
          final long column = message.hasColumn() ? message.getColumn() : -1;
          final String srcUrl = sourceFilePath != null ? VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, sourceFilePath) : null;
          compileContext.addMessage(category, messageText, srcUrl, (int)line, (int)column);
        }
      }

      @Override
      protected void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event) {
        final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type eventType = event.getEventType();
        switch (eventType) {
          case FILES_GENERATED:
            final List<CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile> generated = event.getGeneratedFilesList();
            final CompilationStatusListener publisher = !myProject.isDisposed()? messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS) : null;
            Set<String> writtenArtifactOutputPaths = outputToArtifact != null ? new THashSet<>(FileUtil.PATH_HASHING_STRATEGY) : null;
            for (CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile generatedFile : generated) {
              final String root = FileUtil.toSystemIndependentName(generatedFile.getOutputRoot());
              final String relativePath = FileUtil.toSystemIndependentName(generatedFile.getRelativePath());
              if (publisher != null) {
                publisher.fileGenerated(root, relativePath);
              }
              if (outputToArtifact != null) {
                Collection<Artifact> artifacts = outputToArtifact.get(root);
                if (!artifacts.isEmpty()) {
                  for (Artifact artifact : artifacts) {
                    ArtifactsCompiler.addChangedArtifact(compileContext, artifact);
                  }
                  writtenArtifactOutputPaths.add(FileUtil.toSystemDependentName(DeploymentUtil.appendToPath(root, relativePath)));
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
              final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status completionStatus = event.getCompletionStatus();
              switch (completionStatus) {
                case CANCELED:
                  status = ExitStatus.CANCELLED;
                  break;
                case ERRORS:
                  status = ExitStatus.ERRORS;
                  break;
                case SUCCESS:
                  status = ExitStatus.SUCCESS;
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
               final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.CustomBuilderMessage message = event.getCustomBuilderMessage();
               if (GlobalOptions.JPS_SYSTEM_BUILDER_ID.equals(message.getBuilderId()) && GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID.equals(message.getMessageType())) {
                 final String text = message.getMessageText();
                 if (!StringUtil.isEmpty(text)) {
                   compileContext.addMessage(CompilerMessageCategory.INFORMATION, text, null, -1, -1);
                 }
               }
             }
             break;

        }
      }
    });
  }


  private static final Key<ExitStatus> COMPILE_SERVER_BUILD_STATUS = Key.create("COMPILE_SERVER_BUILD_STATUS");

  private void startup(final CompileScope scope,
                       final boolean isRebuild,
                       final boolean forceCompile,
                       final CompileStatusNotification callback,
                       final CompilerMessage message) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final String contentName =
      forceCompile ? CompilerBundle.message("compiler.content.name.compile") : CompilerBundle.message("compiler.content.name.make");
    final boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    final CompilerTask compileTask = new CompilerTask(myProject, contentName, isUnitTestMode, true, true, isCompilationStartedAutomatically(scope));

    StatusBar.Info.set("", myProject, "Compiler");
    // ensure the project model seen by build process is up-to-date
    myProject.save();
    if (!isUnitTestMode) {
      ApplicationManager.getApplication().saveSettings();
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final CompileContextImpl compileContext = new CompileContextImpl(myProject, compileTask, scope, !isRebuild && !forceCompile, isRebuild);

    final Runnable compileWork = () -> {
      final ProgressIndicator indicator = compileContext.getProgressIndicator();
      if (indicator.isCanceled() || myProject.isDisposed()) {
        if (callback != null) {
          callback.finished(true, 0, 0, compileContext);
        }
        return;
      }
      try {
        LOG.info("COMPILATION STARTED (BUILD PROCESS)");
        if (message != null) {
          compileContext.addMessage(message);
        }
        if (isRebuild) {
          CompilerUtil.runInContext(compileContext, "Clearing build system data...",
                                    (ThrowableRunnable<Throwable>)() -> CompilerCacheManager.getInstance(myProject).clearCaches(compileContext));
        }
        final boolean beforeTasksOk = executeCompileTasks(compileContext, true);

        final int errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR);
        if (!beforeTasksOk || errorCount > 0) {
          COMPILE_SERVER_BUILD_STATUS.set(compileContext, errorCount > 0 ? ExitStatus.ERRORS : ExitStatus.CANCELLED);
          return;
        }

        final TaskFuture future = compileInExternalProcess(compileContext, false);
        if (future != null) {
          while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
            if (indicator.isCanceled()) {
              future.cancel(false);
            }
          }
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
        CompilerCacheManager.getInstance(myProject).flushCaches();

        final long duration = notifyCompilationCompleted(compileContext, callback, COMPILE_SERVER_BUILD_STATUS.get(compileContext));
        CompilerUtil.logDuration(
          "\tCOMPILATION FINISHED (BUILD PROCESS); Errors: " +
            compileContext.getMessageCount(CompilerMessageCategory.ERROR) +
            "; warnings: " +
            compileContext.getMessageCount(CompilerMessageCategory.WARNING),
          duration
        );
      }
    };

    compileTask.start(compileWork, () -> {
      if (isRebuild) {
        final int rv = Messages.showOkCancelDialog(
            myProject, "You are about to rebuild the whole project.\nRun 'Make Project' instead?", "Confirm Project Rebuild",
            "Make", "Rebuild", Messages.getQuestionIcon()
        );
        if (rv == Messages.OK /*yes, please, do run make*/) {
          startup(scope, false, false, callback, null);
          return;
        }
      }
      startup(scope, isRebuild, forceCompile, callback, message);
    });
  }

  @Nullable @TestOnly
  public static ExitStatus getExternalBuildExitStatus(CompileContext context) {
    return context.getUserData(COMPILE_SERVER_BUILD_STATUS);
  }

  /** @noinspection SSBasedInspection*/
  private long notifyCompilationCompleted(final CompileContextImpl compileContext, final CompileStatusNotification callback, final ExitStatus _status) {
    final long duration = System.currentTimeMillis() - compileContext.getStartCompilationStamp();
    if (!myProject.isDisposed()) {
      // refresh on output roots is required in order for the order enumerator to see all roots via VFS
      final Module[] affectedModules = compileContext.getCompileScope().getAffectedModules();

      if (_status != ExitStatus.UP_TO_DATE && _status != ExitStatus.CANCELLED) {
        // have to refresh in case of errors too, because run configuration may be set to ignore errors
        Collection<String> affectedRoots = ContainerUtil.newHashSet(CompilerPathsEx.getOutputPaths(affectedModules));
        if (!affectedRoots.isEmpty()) {
          ProgressIndicator indicator = compileContext.getProgressIndicator();
          indicator.setText("Synchronizing output directories...");
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
          ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.MESSAGES_WINDOW, messageType, statusMessage);
        }

        final String wrappedMessage = _status != ExitStatus.UP_TO_DATE? "<a href='#'>" + statusMessage + "</a>" : statusMessage;
        final Notification notification = CompilerManager.NOTIFICATION_GROUP.createNotification(
          "", wrappedMessage,
          messageType.toNotificationType(),
          new MessagesActivationListener(compileContext)
        ).setImportant(false);
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

  private static String createStatusMessage(final ExitStatus status, final int warningCount, final int errorCount, long duration) {
    String message;
    if (status == ExitStatus.CANCELLED) {
      message = CompilerBundle.message("status.compilation.aborted");
    }
    else if (status == ExitStatus.UP_TO_DATE) {
      message = CompilerBundle.message("status.all.up.to.date");
    }
    else  {
      if (status == ExitStatus.SUCCESS) {
        message = warningCount > 0
               ? CompilerBundle.message("status.compilation.completed.successfully.with.warnings", warningCount)
               : CompilerBundle.message("status.compilation.completed.successfully");
      }
      else {
        message = CompilerBundle.message("status.compilation.completed.successfully.with.warnings.and.errors", errorCount, warningCount);
      }
      message = message + " in " + StringUtil.formatDuration(duration);
    }
    return message;
  }

  /*
  private void dropScopesCaches() {
    // hack to be sure the classpath will include the output directories
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).clearScopesCachesForModules();
      }
    });
  }
  */

  // [mike] performance optimization - this method is accessed > 15,000 times in Aurora
  private String getModuleOutputPath(final Module module, boolean inTestSourceContent) {
    final Map<Module, String> map = inTestSourceContent ? myModuleTestOutputPaths : myModuleOutputPaths;
    String path = map.get(module);
    if (path == null) {
      path = CompilerPaths.getModuleOutputPath(module, inTestSourceContent);
      map.put(module, path);
    }

    return path;
  }

  public void executeCompileTask(final CompileTask task, final CompileScope scope, final String contentName, final Runnable onTaskFinished) {
    final CompilerTask progressManagerTask = new CompilerTask(myProject, contentName, false, false, true, isCompilationStartedAutomatically(scope));
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

  private boolean executeCompileTasks(final CompileContext context, final boolean beforeTasks) {
    if (myProject.isDisposed()) {
      return false;
    }
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    progressIndicator.pushState();
    try {
      CompileTask[] tasks = beforeTasks ? manager.getBeforeTasks() : manager.getAfterTasks();
      if (tasks.length > 0) {
        progressIndicator.setText(beforeTasks
                                  ? CompilerBundle.message("progress.executing.precompile.tasks")
                                  : CompilerBundle.message("progress.executing.postcompile.tasks"));
        for (CompileTask task : tasks) {
          if (!task.execute(context)) {
            return false;
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
      if (progressIndicator instanceof CompilerTask) {
        ApplicationManager.getApplication().invokeLater(((CompilerTask)progressIndicator)::showCompilerContent);
      }
    }
    return true;
  }

  private boolean validateCompilerConfiguration(final CompileScope scope) {
    try {
      final Module[] scopeModules = scope.getAffectedModules();
      final List<String> modulesWithoutOutputPathSpecified = new ArrayList<>();
      final List<String> modulesWithoutJdkAssigned = new ArrayList<>();
      final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      for (final Module module : scopeModules) {
        if (!compilerManager.isValidationEnabled(module)) {
          continue;
        }
        final boolean hasSources = hasSources(module, JavaSourceRootType.SOURCE);
        final boolean hasTestSources = hasSources(module, JavaSourceRootType.TEST_SOURCE);
        if (!hasSources && !hasTestSources) {
          // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
          // todo still there may be problems with this approach if some generated files are attributed by this module
          continue;
        }
        final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
        if (jdk == null) {
          modulesWithoutJdkAssigned.add(module.getName());
        }
        final String outputPath = getModuleOutputPath(module, false);
        final String testsOutputPath = getModuleOutputPath(module, true);
        if (outputPath == null && testsOutputPath == null) {
          modulesWithoutOutputPathSpecified.add(module.getName());
        }
        else {
          if (outputPath == null) {
            if (hasSources) {
              modulesWithoutOutputPathSpecified.add(module.getName());
            }
          }
          if (testsOutputPath == null) {
            if (hasTestSources) {
              modulesWithoutOutputPathSpecified.add(module.getName());
            }
          }
        }
      }
      if (!modulesWithoutJdkAssigned.isEmpty()) {
        showNotSpecifiedError("error.jdk.not.specified", modulesWithoutJdkAssigned, ProjectBundle.message("modules.classpath.title"));
        return false;
      }

      if (!modulesWithoutOutputPathSpecified.isEmpty()) {
        showNotSpecifiedError("error.output.not.specified", modulesWithoutOutputPathSpecified, CommonContentEntriesEditor.NAME);
        return false;
      }

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
              showCyclicModulesHaveDifferentJdksError(ModuleSourceSet.getModules(sourceSets));
              return false;
            }
          }
  
          LanguageLevel moduleLanguageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
          if (languageLevel == null) {
            languageLevel = moduleLanguageLevel;
          }
          else {
            if (!languageLevel.equals(moduleLanguageLevel)) {
              showCyclicModulesHaveDifferentLanguageLevel(ModuleSourceSet.getModules(sourceSets));
              return false;
            }
          }
        }
      }
      return true;
    }
    catch (Throwable e) {
      LOG.info(e);
      return false;
    }
  }

  private void showCyclicModulesHaveDifferentLanguageLevel(Set<Module> modulesInChunk) {
    Module firstModule = ContainerUtil.getFirstItem(modulesInChunk);
    LOG.assertTrue(firstModule != null);
    String moduleNameToSelect = firstModule.getName();
    final String moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.language.level", moduleNames),
                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private void showCyclicModulesHaveDifferentJdksError(Set<Module> modulesInChunk) {
    Module firstModule = ContainerUtil.getFirstItem(modulesInChunk);
    LOG.assertTrue(firstModule != null);
    String moduleNameToSelect = firstModule.getName();
    final String moduleNames = getModulesString(modulesInChunk);
    Messages.showMessageDialog(myProject, CompilerBundle.message("error.chunk.modules.must.have.same.jdk", moduleNames),
                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(moduleNameToSelect, null);
  }

  private static String getModulesString(Collection<Module> modulesInChunk) {
    final StringBuilder moduleNames = StringBuilderSpinAllocator.alloc();
    try {
      for (Module module : modulesInChunk) {
        if (moduleNames.length() > 0) {
          moduleNames.append("\n");
        }
        moduleNames.append("\"").append(module.getName()).append("\"");
      }
      return moduleNames.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(moduleNames);
    }
  }

  private static boolean hasSources(Module module, final JavaSourceRootType rootType) {
    return !ModuleRootManager.getInstance(module).getSourceRoots(rootType).isEmpty();
  }

  private void showNotSpecifiedError(@NonNls final String resourceId, List<String> modules, String editorNameToSelect) {
    String nameToSelect = null;
    final StringBuilder names = StringBuilderSpinAllocator.alloc();
    final String message;
    try {
      final int maxModulesToShow = 10;
      for (String name : modules.size() > maxModulesToShow ? modules.subList(0, maxModulesToShow) : modules) {
        if (nameToSelect == null) {
          nameToSelect = name;
        }
        if (names.length() > 0) {
          names.append(",\n");
        }
        names.append("\"");
        names.append(name);
        names.append("\"");
      }
      if (modules.size() > maxModulesToShow) {
        names.append(",\n...");
      }
      message = CompilerBundle.message(resourceId, modules.size(), names.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(names);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(message);
    }

    Messages.showMessageDialog(myProject, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    showConfigurationDialog(nameToSelect, editorNameToSelect);
  }

  private void showConfigurationDialog(String moduleNameToSelect, String tabNameToSelect) {
    ProjectSettingsService.getInstance(myProject).showModuleConfigurationDialog(moduleNameToSelect, tabNameToSelect);
  }

  private static class MessagesActivationListener extends NotificationListener.Adapter {
    private final WeakReference<Project> myProjectRef;
    private final Object myContentId;

    public MessagesActivationListener(CompileContextImpl compileContext) {
      myProjectRef = new WeakReference<>(compileContext.getProject());
      myContentId = compileContext.getBuildSession().getContentId();
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      final Project project = myProjectRef.get();
      if (project != null && !project.isDisposed() && CompilerTask.showCompilerContent(project, myContentId)) {
        final ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (tw != null) {
          tw.activate(null, false);
        }
      }
      else {
        notification.expire();
      }
    }
  }
}
