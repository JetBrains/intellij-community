// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Activities;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StartupManagerImpl extends StartupManagerEx {
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
    checkBeforeAddingPostStartupActivity();
    Deque<Runnable> list = DumbService.isDumbAware(runnable) ? myDumbAwarePostStartupActivities : myNotDumbAwarePostStartupActivities;
    synchronized (myLock) {
      list.add(runnable);
    }
  }

  @Override
  public void registerPostStartupDumbAwareActivity(@NotNull Runnable runnable) {
    checkBeforeAddingPostStartupActivity();
    synchronized (myLock) {
      myDumbAwarePostStartupActivities.add(runnable);
    }
  }

  private void checkBeforeAddingPostStartupActivity() {
    checkNonDefaultProject();
    if (myPostStartupActivitiesPassed) {
      LOG.error("Registering post-startup activity that will never be run:" +
                " disposed=" + myProject.isDisposed() + "; open=" + myProject.isOpen() +
                "; passed=" + myStartupActivitiesPassed);
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
        runActivities(myPreStartupActivities, Activities.PROJECT_PRE_STARTUP);

        // to avoid atomicity issues if runWhenProjectIsInitialized() is run at the same time
        synchronized (this) {
          myPreStartupActivitiesPassed = true;
        }

        runActivities(myStartupActivities, Activities.PROJECT_STARTUP);

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
    // (depending on dumb-awareness), but because there is no other concurrent phase,
    // we measure it as a sequential activity to put it on the timeline and make clear what's going on the end (avoid last "unknown" phase)
    Activity dumbAwareActivity = StartUpMeasurer.startMainActivity(Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES);

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

    StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(new ExtensionPointListener<StartupActivity>() {
      @Override
      public void extensionAdded(@NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (DumbService.isDumbAware(extension)) {
          runActivity(new AtomicBoolean(), extension, pluginDescriptor);
        }
        else {
          dumbService.runWhenSmart(() -> runActivity(new AtomicBoolean(), extension, pluginDescriptor));
        }
      }
    }, myProject);
  }

  private void runActivity(@NotNull AtomicBoolean uiFreezeWarned, @NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
    }

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
      if (indicator != null) {
        indicator.popState();
      }
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

  // runs in EDT
  public void runPostStartupActivities() {
    if (postStartupActivityPassed()) {
      return;
    }

    runActivities(myDumbAwarePostStartupActivities, Activities.PROJECT_DUMB_POST_STARTUP);

    DumbService dumbService = DumbService.getInstance(myProject);
    dumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        // myDumbAwarePostStartupActivities might be non-empty if new activities were registered during dumb mode
        runActivities(myDumbAwarePostStartupActivities, Activities.PROJECT_DUMB_POST_STARTUP);

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

    //noinspection TestOnlyProblems
    if (!myProject.isDisposed() && !ProjectManagerImpl.isLight(myProject)) {
      scheduleBackgroundPostStartupActivities();
    }
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

  private void runActivities(@NotNull Deque<? extends Runnable> activities, @NotNull String activityName) {
    if (activities.isEmpty()) {
      return;
    }

    Activity activity = StartUpMeasurer.startMainActivity(activityName);

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

  private void scheduleBackgroundPostStartupActivities() {
    if (myProject.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    ScheduledFuture<?> scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      List<StartupActivity.Background> activities = StartupActivity.BACKGROUND_POST_STARTUP_ACTIVITY.getExtensionList();
      StartupActivity.BACKGROUND_POST_STARTUP_ACTIVITY.addExtensionPointListener(new ExtensionPointListener<StartupActivity.Background>() {
        @Override
        public void extensionAdded(@NotNull StartupActivity.Background extension, @NotNull PluginDescriptor pluginDescriptor) {
          extension.runActivity(myProject);
        }
      }, myProject);

      BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
        for (StartupActivity activity : activities) {
          ProgressManager.checkCanceled();

          if (myProject.isDisposed()) {
            return;
          }

          activity.runActivity(myProject);
        }
      });
    }, Registry.intValue("ide.background.post.startup.activity.delay"), TimeUnit.MILLISECONDS);
    Disposer.register(myProject, () -> {
      scheduledFuture.cancel(false);
    });
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
  public void runWhenProjectIsInitialized(@NotNull Runnable action) {
    Application app = ApplicationManager.getApplication();
    if (app == null) {
      return;
    }

    if (app.isDispatchThread()) {
      if (canRunOnInitialized(action, app)) {
        action.run();
      }
    }
    else {
      app.invokeLater(() -> {
        if (canRunOnInitialized(action, app)) {
          action.run();
        }
      }, myProject.getDisposed());
    }
  }

  private synchronized boolean canRunOnInitialized(@NotNull Runnable action, Application app) {
    if (myProject.isDisposed()) {
      return false;
    }

    // in tests that simulate project opening, post-startup activities could have been run already
    // then we should act as if the project was initialized
    boolean initialized = myProject.isInitialized() || myProject.isDefault() || (myPostStartupActivitiesPassed && app.isUnitTestMode());
    if (initialized) {
      return true;
    }

    registerPostStartupActivity(action);
    return false;
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