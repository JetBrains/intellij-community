// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.impl.hotswap.HotSwapDebugSessionManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.*;
import com.intellij.task.impl.ProjectTaskManagerImpl;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.impl.hotswap.HotSwapStatusNotificationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class HotSwapUIImpl extends HotSwapUI {
  /**
   * There are cases when hotswapping the changed classes is not needed.
   * For example, for a Tomcat application 'redeploy',
   * all classes are replaced anyway using a fresh class loader,
   * so there's no need to try reloading classes,
   * as that could only cause hotswap failure UX issues.
   * <p>
   * The flag can be used to skip hotswap after the {@link ProjectTaskManager} run session finished.
   * To apply the flag, add it to the {@link ProjectTaskContext} user data.
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
    return ContainerUtil.exists(sessions, DebuggerSession::isPaused);
  }

  /**
   * After a compilation has finished successfully,
   * decide which sessions and classes participate in the hotswap and reload them.
   *
   * @param generatedPaths the relative paths of the {@code .class} files that were compiled, grouped by their content root
   */
  private void hotSwapSessions(@NotNull List<DebuggerSession> sessions,
                               @Nullable Map<String, Collection<String>> generatedPaths,
                               @Nullable NotNullLazyValue<List<String>> outputPaths,
                               @Nullable HotSwapStatusListener callback) {
    boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
    myAskBeforeHotswap = true;

    DebuggerSettings settings = DebuggerSettings.getInstance();
    String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
    boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

    HotSwapStatusListener statusListener = makeNullSafe(callback);

    if (shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {
      statusListener.onCancel(sessions);
      return;
    }

    List<DebuggerSession> toScan = new ArrayList<>(sessions); // by default scan all sessions
    List<DebuggerSession> toUseGenerated = new ArrayList<>();

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

    HotSwapProgressImpl findClassesProgress = !toScan.isEmpty() ? createHotSwapProgress(statusListener, sessions) : null;
    HotSwapProgressImpl outputPathsProgress =
      !toUseGenerated.isEmpty() && outputPaths != null ? createHotSwapProgress(statusListener, sessions) : null;

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
        modifiedClasses.putAll(scanForModifiedClassesWithProgress(toScan, null, findClassesProgress));
      }

      if (modifiedClasses.isEmpty()) {
        String message = JavaDebuggerBundle.message("status.hotswap.uptodate");
        Notification notification = HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION);
        HotSwapStatusNotificationManager.getInstance(myProject).trackNotification(notification);
        notification.notify(myProject);
        statusListener.onNothingToReload(sessions);
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        if (shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
          RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions, shouldDisplayHangWarning);
          if (!dialog.showAndGet()) {
            for (DebuggerSession session : modifiedClasses.keySet()) {
              session.setModifiedClassesScanRequired(true);
            }
            statusListener.onCancel(sessions);
            return;
          }
          Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
          for (DebuggerSession session : modifiedClasses.keySet()) {
            if (!toReload.contains(session)) {
              session.setModifiedClassesScanRequired(true);
            }
          }
          modifiedClasses.keySet().retainAll(toReload);
        }
        else if (shouldDisplayHangWarning && !confirmPossibleHang(settings)) {
          for (DebuggerSession session : modifiedClasses.keySet()) {
            session.setModifiedClassesScanRequired(true);
          }
          statusListener.onCancel(sessions);
          return;
        }

        if (modifiedClasses.isEmpty()) {
          return; // Without calling onCancel.
        }

        HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
        if (modifiedClasses.keySet().size() == 1) {
          progress.setSessionForActions(ContainerUtil.getFirstItem(modifiedClasses.keySet()));
        }
        progress.addProgressListener(delegatingTo(statusListener, sessions, progress));

        ApplicationManager.getApplication().executeOnPooledThread(
          () -> reloadModifiedClasses(modifiedClasses, progress)
        );
      }, ModalityState.nonModal());
    });
  }

  private static HotSwapProgressImpl.HotSwapProgressListener delegatingTo(
    HotSwapStatusListener statusListener, @NotNull List<DebuggerSession> sessions, HotSwapProgressImpl progress
  ) {
    return new HotSwapProgressImpl.HotSwapProgressListener() {
      @Override
      public void onCancel() {
        statusListener.onCancel(sessions);
      }

      @Override
      public void onFinish() {
        if (!progress.hasErrors()) {
          statusListener.onSuccess(sessions);
        }
        else {
          statusListener.onFailure(sessions);
        }
      }
    };
  }

  private static boolean confirmPossibleHang(@NotNull DebuggerSettings settings) {
    int answer = Messages.showCheckboxMessageDialog(
      JavaDebuggerBundle.message("hotswap.dialog.hang.warning"),
      JavaDebuggerBundle.message("hotswap.dialog.title"),
      new String[]{
        JavaDebuggerBundle.message("button.perform.reload.classes"),
        JavaDebuggerBundle.message("button.skip.reload.classes"),
      },
      UIBundle.message("dialog.options.do.not.show"),
      false, 1, 1, Messages.getWarningIcon(),
      (exitCode, cb) -> {
        settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
        return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
      }
    );
    return answer != DialogWrapper.CANCEL_EXIT_CODE;
  }

  @NotNull
  private HotSwapProgressImpl createHotSwapProgress(@NotNull HotSwapStatusListener statusListener,
                                                    @NotNull List<DebuggerSession> sessions) {
    HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
    progress.addProgressListener(new HotSwapProgressImpl.HotSwapProgressListener() {
      @Override
      public void onCancel() {
        statusListener.onCancel(sessions);
      }
    });
    return progress;
  }

  @NotNull
  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(@NotNull List<DebuggerSession> sessions,
                                                                                                   @Nullable NotNullLazyValue<List<String>> outputPaths,
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

  private static void reloadModifiedClasses(Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
                                            HotSwapProgressImpl progress) {
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
    Project project = session.getProject();
    callback = mergeCallbacksIfNeeded(callback, HotSwapDebugSessionManager.getInstance(project).createSessionListenerOrNull(session));
    if (compileBeforeHotswap) {
      ProjectTaskManagerImpl.putBuildOriginator(project, this.getClass());
      ProjectTaskManager projectTaskManager = ProjectTaskManager.getInstance(project);
      if (callback == null) {
        projectTaskManager.buildAllModules();
      }
      else {
        ProjectTask buildProjectTask = projectTaskManager.createAllModulesBuildTask(true, project);
        projectTaskManager.run(createContext(callback), buildProjectTask);
      }
    }
    else {
      if (session.isAttached()) {
        hotSwapSessions(Collections.singletonList(session), null, null, callback);
      }
      else if (callback != null) {
        callback.onFailure(List.of(session));
      }
    }
  }

  @Override
  public void compileAndReload(@NotNull DebuggerSession session, VirtualFile @NotNull ... files) {
    dontAskHotswapAfterThisCompilation();
    Project project = session.getProject();
    ProjectTaskManagerImpl.putBuildOriginator(project, this.getClass());

    HotSwapStatusListener callback = HotSwapDebugSessionManager.getInstance(project).createSessionListenerOrNull(session);
    if (callback == null) {
      ProjectTaskManager.getInstance(project).compile(files);
    } else {
      ProjectTaskManagerImpl taskManager = (ProjectTaskManagerImpl)ProjectTaskManager.getInstance(project);
      ProjectTask task = taskManager.createModulesFilesTask(files);
      taskManager.run(createContext(callback), task);
    }
    // The control flow continues at MyCompilationStatusListener.finished.
  }

  private static ProjectTaskContext createContext(@NotNull HotSwapStatusListener callback) {
    return new ProjectTaskContext(callback).withUserData(HOT_SWAP_CALLBACK_KEY, callback);
  }

  private static @Nullable HotSwapStatusListener mergeCallbacksIfNeeded(@Nullable HotSwapStatusListener callback1, @Nullable HotSwapStatusListener callback2) {
    if (callback1 == null) return callback2;
    if (callback2 == null) return callback1;
    return new HotSwapStatusListener() {
      @Override
      public void onSuccess(@NotNull List<DebuggerSession> sessions) {
        callback1.onSuccess(sessions);
        callback2.onSuccess(sessions);
      }

      @Override
      public void onNothingToReload(List<DebuggerSession> sessions) {
        callback1.onNothingToReload(sessions);
        callback2.onNothingToReload(sessions);
      }

      @Override
      public void onCancel(List<DebuggerSession> sessions) {
        callback1.onCancel(sessions);
        callback2.onCancel(sessions);
      }

      @Override
      public void onFailure(List<DebuggerSession> sessions) {
        callback1.onFailure(sessions);
        callback2.onFailure(sessions);
      }
    };
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }

  private static final class MyCompilationStatusListener implements ProjectTaskListener {
    private final Set<File> myOutputRoots;
    private final Project myProject;

    private MyCompilationStatusListener(Project project) {
      myProject = project;
      myOutputRoots = FileCollectionFactory.createCanonicalFileSet();
      for (String path : CompilerPaths.getOutputPaths(ModuleManager.getInstance(myProject).getModules())) {
        myOutputRoots.add(new File(path));
      }
    }

    @Override
    public void started(@NotNull ProjectTaskContext context) {
      context.enableCollectionOfGeneratedFiles();
      ensureListenerIsInstalled(context);
    }

    private void ensureListenerIsInstalled(@NotNull ProjectTaskContext context) {
      HotSwapStatusListener callback = context.getUserData(HOT_SWAP_CALLBACK_KEY);
      if (callback != null) return;
      List<DebuggerSession> sessions = getHotSwappableDebugSessions(myProject);
      HotSwapDebugSessionManager manager = HotSwapDebugSessionManager.getInstance(myProject);
      for (DebuggerSession session : sessions) {
        HotSwapStatusListener listener = manager.createSessionListenerOrNull(session);
        if (listener == null) continue;
        context.putUserData(HOT_SWAP_CALLBACK_KEY, listener);
        return;
      }
    }

    @Override
    public void finished(@NotNull ProjectTaskManager.Result result) {
      ProjectTaskContext context = result.getContext();
      HotSwapStatusListener callback = context.getUserData(HOT_SWAP_CALLBACK_KEY);
      if (myProject.isDisposed()) {
        notifyCancelled(callback, Collections.emptyList());
        return;
      }
      List<DebuggerSession> sessions = getHotSwappableDebugSessions(myProject);
      if (result.hasErrors()) {
        if (callback != null) {
          callback.onFailure(sessions);
        }
        return;
      }
      if (!hasCompilationResults(result)
          || result.isAborted()
          || SKIP_HOT_SWAP_KEY.getRequired(context)
          || sessions.isEmpty()
      ) {
        notifyCancelled(callback, sessions);
        return;
      }

      HotSwapUIImpl instance = (HotSwapUIImpl)getInstance(myProject);
      for (HotSwapVetoableListener listener : instance.myListeners) {
        if (!listener.shouldHotSwap(context)) {
          notifyCancelled(callback, sessions);
          return;
        }
      }

      Map<String, Collection<String>> generatedPaths = collectGeneratedPaths(context);
      NotNullLazyValue<List<String>> outputRoots = context.getDirtyOutputPaths()
        .map(stream -> NotNullLazyValue.createValue(() -> stream.collect(Collectors.toList())))
        .orElse(null);
      instance.hotSwapSessions(sessions, generatedPaths, outputRoots, callback);
    }

    private static void notifyCancelled(@Nullable HotSwapStatusListener callback, List<DebuggerSession> sessions) {
      if (callback != null) {
        callback.onCancel(sessions);
      }
    }

    @NotNull
    private Map<String, Collection<String>> collectGeneratedPaths(ProjectTaskContext context) {
      Collection<String> generatedFilesRoots = context.getGeneratedFilesRoots();
      if (generatedFilesRoots.isEmpty()) return Collections.emptyMap();

      Map<String, Collection<String>> generatedPaths = new HashMap<>();
      for (String outputRoot : generatedFilesRoots) {
        // collect only classes under IDE output roots
        if (!JpsPathUtil.isUnder(myOutputRoots, new File(outputRoot))) continue;
        Collection<String> relativePaths = ContainerUtil.filter(
          context.getGeneratedFilesRelativePaths(outputRoot),
          relativePath -> StringUtil.endsWith(relativePath, ".class")
        );
        if (!relativePaths.isEmpty()) {
          generatedPaths.put(outputRoot, relativePaths);
        }
      }
      return generatedPaths;
    }

    private static boolean hasCompilationResults(@NotNull ProjectTaskManager.Result result) {
      return result.anyTaskMatches(
        (task, state) -> task instanceof ModuleBuildTask && !state.isFailed() && !state.isSkipped()
      );
    }
  }

  public static boolean canHotSwap(@NotNull DebuggerSession debuggerSession) {
    return debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses();
  }

  @NotNull
  private static List<DebuggerSession> getHotSwappableDebugSessions(Project project) {
    return ContainerUtil.filter(DebuggerManagerEx.getInstanceEx(project).getSessions(), HotSwapUIImpl::canHotSwap);
  }

  private static HotSwapStatusListener makeNullSafe(HotSwapStatusListener listener) {
    return new HotSwapStatusListener() {
      @Override
      public void onCancel(List<DebuggerSession> sessions) {
        if (listener != null) listener.onCancel(sessions);
      }

      @Override
      public void onSuccess(List<DebuggerSession> sessions) {
        if (listener != null) listener.onSuccess(sessions);
      }

      @Override
      public void onNothingToReload(List<DebuggerSession> sessions) {
        if (listener != null) listener.onNothingToReload(sessions);
      }

      @Override
      public void onFailure(List<DebuggerSession> sessions) {
        if (listener != null) listener.onFailure(sessions);
      }
    };
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

      MessageBusConnection conn = myConn;
      if (conn != null) {
        Disposer.dispose(conn);
        myConn = null;
      }
    }
  }
}
