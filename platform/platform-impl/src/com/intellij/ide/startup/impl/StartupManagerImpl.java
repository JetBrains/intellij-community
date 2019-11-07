// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.internal.statistic.collectors.fus.project.ProjectFsStatsCollector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.project.ProjectKt;
import com.intellij.ui.GuiUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StartupManagerImpl extends StartupManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(StartupManagerImpl.class);
  private static final long EDT_WARN_THRESHOLD_IN_NANO = TimeUnit.MILLISECONDS.toNanos(100);

  private final Object myLock = new Object();

  private final Deque<Runnable> myPreStartupActivities = new ArrayDeque<>();
  private final Deque<Runnable> myStartupActivities = new ArrayDeque<>();

  private final Deque<Runnable> myDumbAwarePostStartupActivities = new ArrayDeque<>();
  private final Deque<Runnable> myNotDumbAwarePostStartupActivities = new ArrayDeque<>();
  // guarded by this
  private boolean myPostStartupActivitiesPassed;

  private volatile boolean myPreStartupActivitiesPassed;
  private volatile boolean myStartupActivitiesPassed;

  private final Project myProject;
  private ScheduledFuture<?> myBackgroundPostStartupScheduledFuture;

  public StartupManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  private void checkNonDefaultProject() {
    LOG.assertTrue(!myProject.isDefault(), "Please don't register startup activities for the default project: they won't ever be run");
  }

  @Override
  public void registerPreStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    LOG.assertTrue(!myPreStartupActivitiesPassed, "Registering pre startup activity that will never be run");
    synchronized (myLock) {
      myPreStartupActivities.add(runnable);
    }
  }

  @Override
  public void registerStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    LOG.assertTrue(!myStartupActivitiesPassed, "Registering startup activity that will never be run");
    synchronized (myLock) {
      myStartupActivities.add(runnable);
    }
  }

  @Override
  public synchronized void registerPostStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    if (myPostStartupActivitiesPassed) {
      LOG.error("Registering post-startup activity that will never be run:" +
                " disposed=" + myProject.isDisposed() + "; open=" + myProject.isOpen() +
                "; passed=" + myStartupActivitiesPassed);
    }

    Deque<Runnable> list = DumbService.isDumbAware(runnable) ? myDumbAwarePostStartupActivities : myNotDumbAwarePostStartupActivities;
    synchronized (myLock) {
      list.add(runnable);
    }
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
        }

        runActivities(myStartupActivities, Phases.PROJECT_STARTUP);

        synchronized (this) {
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
    // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
    // (depending on dumb-awareness), but because there is no other concurrent phase and timeline end equals to last dumb-aware activity,
    // we measure it as a sequential activity to put it on the timeline and make clear what's going on the end (avoid last "unknown" phase)
    Activity dumbAwareActivity = StartUpMeasurer.startMainActivity("project post-startup dumb-aware activities");

    AtomicReference<Activity> edtActivity = new AtomicReference<>();

    AtomicBoolean uiFreezeWarned = new AtomicBoolean();
    DumbService dumbService = DumbService.getInstance(myProject);

    AtomicInteger counter = new AtomicInteger();
    StartupActivity.POST_STARTUP_ACTIVITY.processWithPluginDescriptor((extension, pluginDescriptor) -> {
      if (DumbService.isDumbAware(extension)) {
        runActivity(uiFreezeWarned, extension, pluginDescriptor);
      }
      else {
        if (edtActivity.get() == null) {
          edtActivity.set(StartUpMeasurer.startMainActivity("project post-startup edt activities"));
        }

        counter.incrementAndGet();
        dumbService.runWhenSmart(() -> {
          runActivity(uiFreezeWarned, extension, pluginDescriptor);
          if (counter.decrementAndGet() == 0) {
            Activity activity = edtActivity.getAndSet(null);
            if (activity != null) {
              activity.end();
            }
          }
        });
      }
    });

    if (counter.get() == 0) {
      Activity activity = edtActivity.getAndSet(null);
      if (activity != null) {
        activity.end();
      }
    }
    dumbAwareActivity.end();
    snapshot.logResponsivenessSinceCreation("Post-startup activities under progress");

    StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(
      new ExtensionPointListener<StartupActivity>() {
        @Override
        public void extensionAdded(@NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
          if (DumbService.isDumbAware(extension)) {
            runActivity(new AtomicBoolean(), extension, pluginDescriptor);
          }
          else {
            dumbService.runWhenSmart(() -> runActivity(new AtomicBoolean(), extension, pluginDescriptor));
          }
        }
      }, this);
  }

  private void runActivity(@NotNull AtomicBoolean uiFreezeWarned, @NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator != null) indicator.pushState();
    long startTime = StartUpMeasurer.getCurrentTime();
    try {
      extension.runActivity(myProject);
    }
    catch (ServiceNotReadyException e) {
      LOG.error(new Exception(e));
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    finally {
      if (indicator != null) indicator.popState();
    }

    String pluginId = pluginDescriptor.getPluginId().getIdString();
    long duration = StartUpMeasurer.addCompletedActivity(startTime, extension.getClass(), ActivityCategory.POST_STARTUP_ACTIVITY, pluginId, StartUpMeasurer.MEASURE_THRESHOLD);
    if (duration > EDT_WARN_THRESHOLD_IN_NANO) {
      reportUiFreeze(uiFreezeWarned);
    }
  }

  private static void reportUiFreeze(@NotNull AtomicBoolean uiFreezeWarned) {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode() && app.isDispatchThread() && uiFreezeWarned.compareAndSet(false, true)) {
      LOG.info(
        "Some post-startup activities freeze UI for noticeable time. Please consider making them DumbAware to run them in background" +
        " under modal progress, or just making them faster to speed up project opening.");
    }
  }

  // Runs in EDT
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
          if (dumbUnaware.isEmpty()) {
            break;
          }

          // queue each activity in smart mode separately so that if one of them starts the dumb mode, the next ones just wait for it to finish
          for (Runnable activity : dumbUnaware) {
            dumbService.runWhenSmart(() -> runActivity(activity));
          }
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
          myProject.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).postStartupActivitiesPassed(myProject);
        }
      }
    });
  }

  @NotNull
  private List<Runnable> takeDumbUnawareStartupActivities() {
    synchronized (myLock) {
      if (myNotDumbAwarePostStartupActivities.isEmpty()) {
        return Collections.emptyList();
      }

      List<Runnable> result = new ArrayList<>(myNotDumbAwarePostStartupActivities);
      myNotDumbAwarePostStartupActivities.clear();
      return result;
    }
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

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
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

  private void runActivities(@NotNull Deque<? extends Runnable> activities, @NotNull String phaseName) {
    Activity activity = StartUpMeasurer.startMainActivity(phaseName);

    while (true) {
      Runnable runnable;
      synchronized (myLock) {
        runnable = activities.pollFirst();
      }

      if (runnable == null) {
        break;
      }

      long startTime = StartUpMeasurer.getCurrentTime();

      ClassLoader loader = runnable.getClass().getClassLoader();
      String pluginId = loader instanceof PluginClassLoader
                        ? ((PluginClassLoader)loader).getPluginId().getIdString()
                        : PluginManagerCore.CORE_ID.getIdString();

      runActivity(runnable);

      StartUpMeasurer.addCompletedActivity(startTime, runnable.getClass(), ActivityCategory.POST_STARTUP_ACTIVITY, pluginId, StartUpMeasurer.MEASURE_THRESHOLD);
    }

    activity.end();
  }

  public final void scheduleBackgroundPostStartupActivities() {
    if (myProject.isDisposed()) {
      return;
    }

    myBackgroundPostStartupScheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      List<StartupActivity.Background> activities = StartupActivity.BACKGROUND_POST_STARTUP_ACTIVITY.getExtensionList();
      StartupActivity.BACKGROUND_POST_STARTUP_ACTIVITY.addExtensionPointListener(new ExtensionPointListener<StartupActivity.Background>() {
        @Override
        public void extensionAdded(@NotNull StartupActivity.Background extension, @NotNull PluginDescriptor pluginDescriptor) {
          extension.runActivity(myProject);
        }
      }, this);

      BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, () -> {
        for (StartupActivity activity : activities) {
          ProgressManager.checkCanceled();

          if (myProject.isDisposed()) {
            return;
          }

          activity.runActivity(myProject);
        }
      });
    }, Registry.intValue("ide.background.post.startup.activity.delay"), TimeUnit.MILLISECONDS);
  }

  @Override
  public void dispose() {
    if (myBackgroundPostStartupScheduledFuture != null) {
      myBackgroundPostStartupScheduledFuture.cancel(false);
    }
  }

  public static void runActivity(@NotNull Runnable runnable) {
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

    GuiUtils.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) return;

      //noinspection SynchronizeOnThis
      synchronized (this) {
        // in tests that simulate project opening, post-startup activities could have been run already
        // then we should act as if the project was initialized
        boolean initialized = myProject.isInitialized() || myProject.isDefault() || (myPostStartupActivitiesPassed && application.isUnitTestMode());
        if (!initialized) {
          registerPostStartupActivity(action);
          return;
        }
      }

      action.run();
    }, ModalityState.defaultModalityState());
  }

  @TestOnly
  public synchronized void prepareForNextTest() {
    synchronized (myLock) {
      myPreStartupActivities.clear();
      myStartupActivities.clear();
      myDumbAwarePostStartupActivities.clear();
      myNotDumbAwarePostStartupActivities.clear();
    }
  }

  @TestOnly
  public synchronized void checkCleared() {
    try {
      synchronized (myLock) {
        assert myStartupActivities.isEmpty() : "Activities: " + myStartupActivities;
        assert myDumbAwarePostStartupActivities.isEmpty() : "DumbAware Post Activities: " + myDumbAwarePostStartupActivities;
        assert myNotDumbAwarePostStartupActivities.isEmpty() : "Post Activities: " + myNotDumbAwarePostStartupActivities;
        assert myPreStartupActivities.isEmpty() : "Pre Activities: " + myPreStartupActivities;
      }
    }
    finally {
      prepareForNextTest();
    }
  }
}