// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.*;
import com.intellij.ui.UIBundle;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.MessageCategory;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class HotSwapUIImpl extends HotSwapUI {
  /**
   * There are cases when the hotswap of the changed classes is not needed.
   * E.g. for a tomcat application redeploy there's no need to even try reloading classes and it's only can cause hotswap failure UX issue.
   * <p>
   * The flag can be used to skip hotswap after the ProjectTaskManager run session finish.
   * To apply the flag one should add it to {@link ProjectTaskContext} user data.
   *
   * @see ProjectTaskContext#withUserData(Key, Object)
   */
  public static final Key<Boolean> SKIP_HOT_SWAP_KEY = KeyWithDefaultValue.create("skip_hotswap_after_this_compilation", false);
  private static final Key<HotSwapStatusListener> HOT_SWAP_CALLBACK_KEY = Key.create("hot_swap_callback");

  private final List<HotSwapVetoableListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myAskBeforeHotswap = true;
  private final Project myProject;

  public HotSwapUIImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void addListener(HotSwapVetoableListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(HotSwapVetoableListener listener) {
    myListeners.remove(listener);
  }

  private static boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions) {
    if (!settings.HOTSWAP_HANG_WARNING_ENABLED) {
      return false;
    }
    // todo: return false if yourkit agent is inactive
    return sessions.stream().anyMatch(DebuggerSession::isPaused);
  }

  private void hotSwapSessions(@NotNull List<DebuggerSession> sessions,
                               @Nullable HotSwapStatusListener callback) {
    hotSwapSessions(sessions, null, null, callback);
  }

  private void hotSwapSessions(@NotNull final List<DebuggerSession> sessions,
                               @Nullable final Map<String, Collection<String>> generatedPaths,
                               @Nullable final NotNullLazyValue<? extends List<String>> outputPaths,
                               @Nullable final HotSwapStatusListener callback) {
    final boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
    myAskBeforeHotswap = true;

    final DebuggerSettings settings = DebuggerSettings.getInstance();
    final String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
    final boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

    HotSwapStatusListener callbackWrapper = new HotSwapStatusListener() {
      @Override
      public void onCancel(List<DebuggerSession> sessions) {
        if (callback != null) {
          callback.onCancel(sessions);
        }
      }

      @Override
      public void onSuccess(List<DebuggerSession> sessions) {
        if (callback != null) {
          callback.onSuccess(sessions);
        }
      }

      @Override
      public void onFailure(List<DebuggerSession> sessions) {
        if (callback != null) {
          callback.onFailure(sessions);
        }
      }
    };

    if (shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {
      callbackWrapper.onCancel(sessions);
      return;
    }

    final List<DebuggerSession> toScan = new ArrayList<>(sessions); // by default scan all sessions
    final List<DebuggerSession> toUseGenerated = new ArrayList<>();

    if (generatedPaths != null) {
      toScan.clear();
      for (DebuggerSession session : sessions) {
        if (session.isModifiedClassesScanRequired()) {
          toScan.add(session);
        }
        else {
          toUseGenerated.add(session);
        }
        session.setModifiedClassesScanRequired(false);
      }
    }

    final HotSwapProgressImpl findClassesProgress = !toScan.isEmpty() ? createHotSwapProgress(callbackWrapper, sessions) : null;
    final HotSwapProgressImpl outputPathsProgress =
      !toUseGenerated.isEmpty() && outputPaths != null ? createHotSwapProgress(callbackWrapper, sessions) : null;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();
      if (!toUseGenerated.isEmpty()) {
        modifiedClasses.putAll(HotSwapManager.findModifiedClasses(toUseGenerated, generatedPaths));
        if (outputPathsProgress != null) {
          scanForModifiedClassesWithProgress(toUseGenerated, outputPaths, outputPathsProgress)
            .forEach(
              (session, map) -> modifiedClasses.merge(session, map, (map1, map2) -> {
                map1.putAll(map2);
                return map1;
              })
            );
        }
      }
      if (findClassesProgress != null) {
        modifiedClasses.putAll(scanForModifiedClassesWithProgress(toScan, findClassesProgress));
      }

      final Application application = ApplicationManager.getApplication();
      if (modifiedClasses.isEmpty()) {
        final String message = JavaDebuggerBundle.message("status.hotswap.uptodate");
        HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(myProject);
        callbackWrapper.onSuccess(sessions);
        return;
      }

      application.invokeLater(() -> {
        if (shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
          final RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions, shouldDisplayHangWarning);
          if (!dialog.showAndGet()) {
            for (DebuggerSession session : modifiedClasses.keySet()) {
              session.setModifiedClassesScanRequired(true);
            }
            callbackWrapper.onCancel(sessions);
            return;
          }
          final Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
          for (DebuggerSession session : modifiedClasses.keySet()) {
            if (!toReload.contains(session)) {
              session.setModifiedClassesScanRequired(true);
            }
          }
          modifiedClasses.keySet().retainAll(toReload);
        }
        else {
          if (shouldDisplayHangWarning) {
            final int answer = Messages.showCheckboxMessageDialog(
              JavaDebuggerBundle.message("hotswap.dialog.hang.warning"),
              JavaDebuggerBundle.message("hotswap.dialog.title"),
              new String[]{"Perform &Reload Classes", "&Skip Reload Classes"},
              UIBundle.message("dialog.options.do.not.show"),
              false, 1, 1, Messages.getWarningIcon(),
              (exitCode, cb) -> {
                settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
                return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
              }
            );
            if (answer == DialogWrapper.CANCEL_EXIT_CODE) {
              for (DebuggerSession session : modifiedClasses.keySet()) {
                session.setModifiedClassesScanRequired(true);
              }
              callbackWrapper.onCancel(sessions);
              return;
            }
          }
        }

        if (!modifiedClasses.isEmpty()) {
          final HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
          if (modifiedClasses.keySet().size() == 1) {
            progress.setSessionForActions(ContainerUtil.getFirstItem(modifiedClasses.keySet()));
          }
          progress.addProgressListener(new HotSwapProgressImpl.HotSwapProgressListener() {
            @Override
            public void onCancel() {
              callbackWrapper.onCancel(sessions);
            }

            @Override
            public void onFinish() {
              if (progress.getMessages(MessageCategory.ERROR).isEmpty()) {
                callbackWrapper.onSuccess(sessions);
              }
              else {
                callbackWrapper.onFailure(sessions);
              }
            }
          });
          application.executeOnPooledThread(() -> reloadModifiedClasses(modifiedClasses, progress));
        }
      }, ModalityState.NON_MODAL);
    });
  }

  @NotNull
  private HotSwapProgressImpl createHotSwapProgress(@NotNull HotSwapStatusListener callbackWrapper,
                                                    @NotNull List<DebuggerSession> sessions) {
    HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
    progress.addProgressListener(new HotSwapProgressImpl.HotSwapProgressListener() {
      @Override
      public void onCancel() {
        callbackWrapper.onCancel(sessions);
      }
    });
    return progress;
  }

  @NotNull
  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(@NotNull List<DebuggerSession> sessions,
                                                                                                   @NotNull HotSwapProgressImpl progress) {
    return scanForModifiedClassesWithProgress(sessions, null, progress);
  }

  @NotNull
  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(@NotNull List<DebuggerSession> sessions,
                                                                                                   @Nullable NotNullLazyValue<? extends List<String>> outputPaths,
                                                                                                   @NotNull HotSwapProgressImpl progress) {
    return ProgressManager.getInstance().runProcess(() -> {
      try {
        return HotSwapManager.scanForModifiedClasses(sessions, outputPaths, progress);
      }
      finally {
        progress.finished();
      }
    }, progress.getProgressIndicator());
  }

  private static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
                                            final HotSwapProgressImpl progress) {
    ProgressManager.getInstance().runProcess(() -> {
      HotSwapManager.reloadModifiedClasses(modifiedClasses, progress);
      progress.finished();
    }, progress.getProgressIndicator());
  }

  @Override
  public void reloadChangedClasses(@NotNull DebuggerSession session, boolean compileBeforeHotswap) {
    reloadChangedClasses(session, compileBeforeHotswap, null);
  }

  @Override
  public void reloadChangedClasses(@NotNull DebuggerSession session,
                                   boolean compileBeforeHotswap,
                                   @Nullable HotSwapStatusListener callback) {
    dontAskHotswapAfterThisCompilation();
    if (compileBeforeHotswap) {
      ProjectTaskManager projectTaskManager = ProjectTaskManager.getInstance(session.getProject());
      if (callback == null) {
        projectTaskManager.buildAllModules();
      }
      else {
        ProjectTask buildProjectTask = projectTaskManager.createAllModulesBuildTask(true, session.getProject());
        ProjectTaskContext context = new ProjectTaskContext(callback).withUserData(HOT_SWAP_CALLBACK_KEY, callback);
        projectTaskManager.run(context, buildProjectTask);
      }
    }
    else {
      if (session.isAttached()) {
        hotSwapSessions(Collections.singletonList(session), callback);
      }
      else if (callback != null) {
        callback.onFailure(new SmartList<>(session));
      }
    }
  }

  @Override
  public void compileAndReload(@NotNull DebuggerSession session, VirtualFile @NotNull ... files) {
    dontAskHotswapAfterThisCompilation();
    ProjectTaskManager.getInstance(session.getProject()).compile(files);
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }

  private static class MyCompilationStatusListener implements ProjectTaskListener {

    private final THashSet<File> myOutputRoots;
    private final Project myProject;

    private MyCompilationStatusListener(Project project) {
      myProject = project;
      myOutputRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      for (final String path : CompilerPaths.getOutputPaths(ModuleManager.getInstance(myProject).getModules())) {
        myOutputRoots.add(new File(path));
      }
    }

    @Override
    public void started(@NotNull ProjectTaskContext context) {
      context.enableCollectionOfGeneratedFiles();
    }

    @Override
    public void finished(@NotNull ProjectTaskManager.Result result) {
      if (myProject.isDisposed()) return;
      if (!hasCompilationResults(result)) return;

      ProjectTaskContext context = result.getContext();
      if (!result.hasErrors() && !result.isAborted() && !SKIP_HOT_SWAP_KEY.getRequired(context)) {
        HotSwapUIImpl instance = (HotSwapUIImpl)getInstance(myProject);
        for (HotSwapVetoableListener listener : instance.myListeners) {
          if (!listener.shouldHotSwap(context)) {
            return;
          }
        }

        List<DebuggerSession> sessions = getHotSwappableDebugSessions(myProject);
        if (!sessions.isEmpty()) {
          Map<String, Collection<String>> generatedPaths;
          Collection<String> generatedFilesRoots = context.getGeneratedFilesRoots();
          if (!generatedFilesRoots.isEmpty()) {
            generatedPaths = new HashMap<>();
            for (String outputRoot : generatedFilesRoots) {
              // collect only classes under IDE output roots
              if (!JpsPathUtil.isUnder(myOutputRoots, new File(outputRoot))) continue;
              Collection<String> relativePaths = context.getGeneratedFilesRelativePaths(outputRoot).stream()
                .filter(relativePath -> StringUtil.endsWith(relativePath, ".class"))
                .collect(Collectors.toCollection(SmartList::new));
              if (!relativePaths.isEmpty()) {
                generatedPaths.put(outputRoot, relativePaths);
              }
            }
          }
          else {
            generatedPaths = Collections.emptyMap();
          }

          HotSwapStatusListener callback = context.getUserData(HOT_SWAP_CALLBACK_KEY);
          NotNullLazyValue<? extends List<String>> outputRoots = context.getDirtyOutputPaths()
            .map(stream -> NotNullLazyValue.createValue(() -> stream.collect(Collectors.toCollection(SmartList::new)))).orElse(null);
          instance.hotSwapSessions(sessions, generatedPaths, outputRoots, callback);
        }
      }
    }

    private static boolean hasCompilationResults(@NotNull ProjectTaskManager.Result result) {
      return result.anyTaskMatches((task, state) -> task instanceof ModuleBuildTask &&
                                              !state.isFailed() && !state.isSkipped());
    }
  }

  public static boolean canHotSwap(@NotNull DebuggerSession debuggerSession) {
    return debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses();
  }

  @NotNull
  private static List<DebuggerSession> getHotSwappableDebugSessions(Project project) {
    return DebuggerManagerEx.getInstanceEx(project).getSessions().stream()
      .filter(HotSwapUIImpl::canHotSwap)
      .collect(Collectors.toCollection(SmartList::new));
  }

  public static class HotSwapDebuggerManagerListener implements DebuggerManagerListener {
    private @NotNull final Project myProject;
    private MessageBusConnection myConn;

    public HotSwapDebuggerManagerListener(@NotNull Project project) {
      myProject = project;
      myConn = null;
    }

    @Override
    public void sessionAttached(DebuggerSession session) {
      if (myConn == null) {
        myConn = myProject.getMessageBus().connect();
        myConn.subscribe(ProjectTaskListener.TOPIC, new MyCompilationStatusListener(myProject));
      }
    }

    @Override
    public void sessionDetached(DebuggerSession session) {
      if (!getHotSwappableDebugSessions(myProject).isEmpty()) return;

      final MessageBusConnection conn = myConn;
      if (conn != null) {
        Disposer.dispose(conn);
        myConn = null;
      }
    }
  }
}
