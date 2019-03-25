// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.internal.statistic.collectors.fus.project.ProjectFsStatsCollector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.project.ProjectKt;
import com.intellij.ui.GuiUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class StartupManagerImpl extends StartupManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private final List<Runnable> myPreStartupActivities = Collections.synchronizedList(new LinkedList<>());
  private final List<Runnable> myStartupActivities = Collections.synchronizedList(new LinkedList<>());

  private final List<Runnable> myDumbAwarePostStartupActivities = Collections.synchronizedList(new LinkedList<>());
  private final List<Runnable> myNotDumbAwarePostStartupActivities = Collections.synchronizedList(new LinkedList<>());
  private boolean myPostStartupActivitiesPassed; // guarded by this

  private volatile boolean myPreStartupActivitiesPassed;
  private volatile boolean myStartupActivitiesRunning;
  private volatile boolean myStartupActivitiesPassed;

  private final Project myProject;
  private boolean myInitialRefreshScheduled;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  private void checkNonDefaultProject() {
    LOG.assertTrue(!myProject.isDefault(), "Please don't register startup activities for the default project: they won't ever be run");
  }

  @Override
  public void registerPreStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    LOG.assertTrue(!myPreStartupActivitiesPassed, "Registering pre startup activity that will never be run");
    myPreStartupActivities.add(runnable);
  }

  @Override
  public void registerStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    LOG.assertTrue(!myStartupActivitiesPassed, "Registering startup activity that will never be run");
    myStartupActivities.add(runnable);
  }

  @Override
  public synchronized void registerPostStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    LOG.assertTrue(!myPostStartupActivitiesPassed, "Registering post-startup activity that will never be run:" +
                                                   " disposed=" + myProject.isDisposed() + "; open=" + myProject.isOpen() +
                                                   "; passed=" + myStartupActivitiesPassed);
    (DumbService.isDumbAware(runnable) ? myDumbAwarePostStartupActivities : myNotDumbAwarePostStartupActivities).add(runnable);
  }

  @Override
  public boolean startupActivityRunning() {
    return myStartupActivitiesRunning;
  }

  @Override
  public boolean startupActivityPassed() {
    return myStartupActivitiesPassed;
  }

  @Override
  public synchronized boolean postStartupActivityPassed() {
    return myPostStartupActivitiesPassed;
  }

  @SuppressWarnings("SynchronizeOnThis")
  public void runStartupActivities() {
    ApplicationManager.getApplication().runReadAction(() -> {
      AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Running Startup Activities");
      try {
        runActivities(myPreStartupActivities, Phases.PROJECT_PRE_STARTUP);

        // to avoid atomicity issues if runWhenProjectIsInitialized() is run at the same time
        synchronized (this) {
          myPreStartupActivitiesPassed = true;
          myStartupActivitiesRunning = true;
        }

        runActivities(myStartupActivities, Phases.PROJECT_STARTUP);

        synchronized (this) {
          myStartupActivitiesRunning = false;
          myStartupActivitiesPassed = true;
        }
      }
      finally {
        token.finish();
      }
    });
  }

  public void runPostStartupActivitiesFromExtensions() {
    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    // strictly speaking, it is not a sequential activity,
    // because sub activities performed in a different threads (depends on dumb awareness),
    // but because there is no any other concurrent phase and timeline end equals to last dumb-aware activity,
    // we measure it as a sequential activity to put on a timeline and make clear what's going on the end (avoid last "unknown" phase).
    Activity activity = StartUpMeasurer.start(Phases.RUN_PROJECT_POST_STARTUP_ACTIVITIES);
    AtomicBoolean uiFreezeWarned = new AtomicBoolean();
    for (StartupActivity extension : StartupActivity.POST_STARTUP_ACTIVITY.getExtensionList()) {
      if (DumbService.isDumbAware(extension)) {
        logActivityDuration(uiFreezeWarned, extension);
      }
      else {
        queueSmartModeActivity(() -> logActivityDuration(uiFreezeWarned, extension));
      }
    }
    activity.end();
    snapshot.logResponsivenessSinceCreation("Post-startup activities under progress");
  }

  private void logActivityDuration(@NotNull AtomicBoolean uiFreezeWarned, @NotNull StartupActivity extension) {
    long startTime = StartUpMeasurer.getCurrentTime();
    extension.runActivity(myProject);
    long duration = ParallelActivity.POST_STARTUP_ACTIVITY.record(startTime, extension.getClass());
    if (duration > 100) {
      Application app = ApplicationManager.getApplication();
      if (!app.isUnitTestMode() && app.isDispatchThread() && uiFreezeWarned.compareAndSet(false, true)) {
        LOG.info(
          "Some post-startup activities freeze UI for noticeable time. Please consider making them DumbAware to run them in background" +
          " under modal progress, or just making them faster to speed up project opening.");
      }
    }
  }

  // queue each activity in smart mode separately so that if one of them starts dumb mode, the next ones just wait for it to finish
  private void queueSmartModeActivity(@NotNull Runnable activity) {
    DumbService.getInstance(myProject).runWhenSmart(() -> runActivity(activity));
  }

  public void runPostStartupActivities() {
    if (postStartupActivityPassed()) {
      return;
    }

    final Application app = ApplicationManager.getApplication();

    if (!app.isHeadlessEnvironment()) {
      checkFsSanity();
      checkProjectRoots();
    }

    runActivities(myDumbAwarePostStartupActivities, Phases.PROJECT_DUMB_POST_STARTUP);

    DumbService dumbService = DumbService.getInstance(myProject);
    dumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        app.assertIsDispatchThread();

        // myDumbAwarePostStartupActivities might be non-empty if new activities were registered during dumb mode
        runActivities(myDumbAwarePostStartupActivities, Phases.PROJECT_DUMB_POST_STARTUP);

        while (true) {
          List<Runnable> dumbUnaware = takeDumbUnawareStartupActivities();
          if (dumbUnaware.isEmpty()) break;

          dumbUnaware.forEach(StartupManagerImpl.this::queueSmartModeActivity);
        }

        if (dumbService.isDumb()) {
          // return here later to process newly submitted activities (if any) and set myPostStartupActivitiesPassed
          dumbService.runWhenSmart(this);
        }
        else {
          //noinspection SynchronizeOnThis
          synchronized (this) {
            myPostStartupActivitiesPassed = true;
          }
        }
      }
    });
  }

  private synchronized List<Runnable> takeDumbUnawareStartupActivities() {
    List<Runnable> result = new ArrayList<>(myNotDumbAwarePostStartupActivities);
    myNotDumbAwarePostStartupActivities.clear();
    return result;
  }

  public void scheduleInitialVfsRefresh() {
    GuiUtils.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed() || myInitialRefreshScheduled) return;

      myInitialRefreshScheduled = true;
      ProjectRootManagerEx.getInstanceEx(myProject).markRootsForRefresh();

      Application app = ApplicationManager.getApplication();
      if (!app.isCommandLine()) {
        final long sessionId = VirtualFileManager.getInstance().asyncRefresh(null);
        final MessageBusConnection connection = app.getMessageBus().connect();
        connection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
          @Override
          public void afterProjectClosed(@NotNull Project project) {
            if (project != myProject) return;

            RefreshQueue.getInstance().cancelSession(sessionId);
            connection.disconnect();
          }
        });
      }
      else {
        VirtualFileManager.getInstance().syncRefresh();
      }
    }, ModalityState.defaultModalityState());
  }

  private void checkFsSanity() {
    try {
      String path = myProject.getProjectFilePath();
      if (path == null || FileUtil.isAncestor(PathManager.getConfigPath(), path, true)) {
        return;
      }
      if (ProjectKt.isDirectoryBased(myProject)) {
        path = PathUtil.getParentPath(path);
      }

      boolean expected = SystemInfo.isFileSystemCaseSensitive;
      boolean actual = FileUtil.isFileSystemCaseSensitive(path);
      LOG.info(path + " case-sensitivity: expected=" + expected + " actual=" + actual);
      if (actual != expected) {
        int prefix = expected ? 1 : 0;  // IDE=true -> FS=false -> prefix='in'
        String title = ApplicationBundle.message("fs.case.sensitivity.mismatch.title");
        String text = ApplicationBundle.message("fs.case.sensitivity.mismatch.message", prefix);
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, text, NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER),
          myProject);
      }

      ProjectFsStatsCollector.caseSensitivity(myProject, actual);
    }
    catch (FileNotFoundException e) {
      LOG.warn(e);
    }
  }

  private void checkProjectRoots() {
    VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentRoots();
    if (roots.length == 0) return;
    LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!(fs instanceof LocalFileSystemImpl)) return;
    FileWatcher watcher = ((LocalFileSystemImpl)fs).getFileWatcher();
    if (!watcher.isOperational()) {
      ProjectFsStatsCollector.watchedRoots(myProject, -1);
      return;
    }

    PooledThreadExecutor.INSTANCE.submit(() -> {
      LOG.debug("FW/roots waiting started");
      while (true) {
        if (myProject.isDisposed()) return;
        if (!watcher.isSettingRoots()) break;
        TimeoutUtil.sleep(10);
      }
      LOG.debug("FW/roots waiting finished");

      Collection<String> manualWatchRoots = watcher.getManualWatchRoots();
      int pctNonWatched = 0;
      if (!manualWatchRoots.isEmpty()) {
        List<String> nonWatched = new SmartList<>();
        for (VirtualFile root : roots) {
          if (!(root.getFileSystem() instanceof LocalFileSystem)) continue;
          String rootPath = root.getPath();
          for (String manualWatchRoot : manualWatchRoots) {
            if (FileUtil.isAncestor(manualWatchRoot, rootPath, false)) {
              nonWatched.add(rootPath);
            }
          }
        }
        if (!nonWatched.isEmpty()) {
          String message = ApplicationBundle.message("watcher.non.watchable.project");
          watcher.notifyOnFailure(message, null);
          LOG.info("unwatched roots: " + nonWatched);
          LOG.info("manual watches: " + manualWatchRoots);
          pctNonWatched = (int)(100.0 * nonWatched.size() / roots.length);
        }
      }

      ProjectFsStatsCollector.watchedRoots(myProject, pctNonWatched);
    });
  }

  public void startCacheUpdate() {
    if (myProject.isDisposed()) return;

    try {
      DumbServiceImpl dumbService = DumbServiceImpl.getInstance(myProject);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        // pre-startup activities have registered dumb tasks that load VFS (scanning files to index)
        // only after these tasks pass does VFS refresh make sense
        dumbService.queueTask(new DumbModeTask() {
          @Override
          public void performInDumbMode(@NotNull ProgressIndicator indicator) {
            scheduleInitialVfsRefresh();
          }

          @Override
          public String toString() {
            return "initial refresh";
          }
        });
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static void runActivities(@NotNull List<? extends Runnable> activities, @NotNull String phaseName) {
    Activity activity = StartUpMeasurer.start(phaseName);
    while (!activities.isEmpty()) {
      runActivity(activities.remove(0));
    }
    activity.end();
  }

  public static void runActivity(Runnable runnable) {
    ProgressManager.checkCanceled();
    try {
      runnable.run();
    }
    catch (ServiceNotReadyException e) {
      LOG.error(new Exception(e));
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable ex) {
      LOG.error(ex);
    }
  }

  @Override
  public void runWhenProjectIsInitialized(@NotNull final Runnable action) {
    final Application application = ApplicationManager.getApplication();
    if (application == null) return;

    Runnable runnable = () -> {
      if (myProject.isDisposed()) return;

      //noinspection SynchronizeOnThis
      synchronized (this) {
        // in tests which simulate project opening, post-startup activities could have been run already.
        // Then we should act as if the project was initialized
        boolean initialized = myProject.isInitialized() || myProject.isDefault() || application.isUnitTestMode() && myPostStartupActivitiesPassed;
        if (!initialized) {
          registerPostStartupActivity(action);
          return;
        }
      }

      action.run();
    };
    GuiUtils.invokeLaterIfNeeded(runnable, ModalityState.defaultModalityState());
  }

  @TestOnly
  public synchronized void prepareForNextTest() {
    myPreStartupActivities.clear();
    myStartupActivities.clear();
    myDumbAwarePostStartupActivities.clear();
    myNotDumbAwarePostStartupActivities.clear();
  }

  @TestOnly
  public synchronized void checkCleared() {
    try {
      assert myStartupActivities.isEmpty() : "Activities: " + myStartupActivities;
      assert myDumbAwarePostStartupActivities.isEmpty() : "DumbAware Post Activities: " + myDumbAwarePostStartupActivities;
      assert myNotDumbAwarePostStartupActivities.isEmpty() : "Post Activities: " + myNotDumbAwarePostStartupActivities;
      assert myPreStartupActivities.isEmpty() : "Pre Activities: " + myPreStartupActivities;
    }
    finally {
      prepareForNextTest();
    }
  }
}