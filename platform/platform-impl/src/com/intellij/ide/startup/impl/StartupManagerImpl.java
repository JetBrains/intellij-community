// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Activities;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.ProjectLoadListener;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public class StartupManagerImpl extends StartupManagerEx {
  private static final Logger LOG = Logger.getInstance(StartupManagerImpl.class);
  private static final long EDT_WARN_THRESHOLD_IN_NANO = TimeUnit.MILLISECONDS.toNanos(100);

  private final Object myLock = new Object();

  private final Deque<Runnable> myStartupActivities = new ArrayDeque<>();

  private final Deque<Runnable> myDumbAwarePostStartupActivities = new ArrayDeque<>();
  private final Deque<Runnable> myNotDumbAwarePostStartupActivities = new ArrayDeque<>();
  private volatile boolean postStartupActivitiesPassed;

  private volatile boolean myStartupActivitiesPassed;

  private final Project myProject;

  public StartupManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  private void checkNonDefaultProject() {
    LOG.assertTrue(!myProject.isDefault(), "Please don't register startup activities for the default project: they won't ever be run");
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
  public void registerPostStartupActivity(@NotNull Runnable runnable) {
    checkBeforeAddingPostStartupActivity();
    Deque<Runnable> list = DumbService.isDumbAware(runnable) ? myDumbAwarePostStartupActivities : myNotDumbAwarePostStartupActivities;
    synchronized (myLock) {
      checkThatPostActivitiesNotPassed();
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
    checkThatPostActivitiesNotPassed();
  }

  private void checkThatPostActivitiesNotPassed() {
    if (postStartupActivitiesPassed) {
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
  public boolean postStartupActivityPassed() {
    return postStartupActivitiesPassed;
  }

  public final void projectOpened(@Nullable ProgressIndicator indicator) {
    if (indicator != null && ApplicationManager.getApplication().isInternal()) {
      indicator.setText(IdeBundle.message("startup.indicator.text.running.startup.activities"));
    }

    doRunStartUpActivities(indicator);

    // If called in EDT - client expect that work will be done after call, executing in a pooled thread maybe not expected.
    // In test mode project opened not under progress, so, execute directly in current thread.
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isDispatchThread()) {
      runPostStartupActivities();
    }
    else {
      if (indicator != null) {
        indicator.checkCanceled();
      }

      app.executeOnPooledThread(() -> {
        if (myProject.isDisposed()) {
          return;
        }

        BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
          runPostStartupActivities();

          ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLoadListener.TOPIC).postStartUpActivitiesPassed();
        });
      });
    }
  }

  private void doRunStartUpActivities(@Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!myStartupActivitiesPassed);

    Activity activity = StartUpMeasurer.startMainActivity(Activities.PROJECT_STARTUP);
    runActivities(myStartupActivities, indicator, null);
    ExtensionsAreaImpl area = (ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea();
    executeActivitiesFromExtensionPoint(indicator, area.getExtensionPoint("com.intellij.startupActivity"));
    myStartupActivitiesPassed = true;
    activity.end();
  }

  private void executeActivitiesFromExtensionPoint(@Nullable ProgressIndicator indicator,
                                                   @SuppressWarnings("SameParameterValue") @NotNull ExtensionPointImpl<StartupActivity> extensionPoint) {
    // use processImplementations to not even create extension if not white-listed
    extensionPoint.processImplementations(/* shouldBeSorted = */ true, (supplier, pluginDescriptor) -> {
      if (myProject.isDisposed()) {
        return;
      }

      PluginId id = pluginDescriptor.getPluginId();
      if (!(id == PluginManagerCore.CORE_ID ||
            id == PluginManagerCore.JAVA_PLUGIN_ID ||
            id.getIdString().equals("com.jetbrains.performancePlugin") ||
            id.getIdString().equals("com.intellij.kotlinNative.platformDeps"))) {
        LOG.error("Only bundled plugin can define " + extensionPoint.getName() + ": " + pluginDescriptor);
        return;
      }

      if (indicator != null) {
        indicator.checkCanceled();
      }

      try {
        runActivity(null, supplier.get(), pluginDescriptor, indicator);
      }
      catch (ExtensionNotApplicableException ignore) {
      }
    });
  }

  @TestOnly
  public void runStartupActivities() {
    if (!myStartupActivitiesPassed) {
      doRunStartUpActivities(null);
    }
  }

  // Must be executed in a pooled thread outside of project loading modal task. The only exclusion - test mode.
  public final void runPostStartupActivities() {
    LOG.assertTrue(myStartupActivitiesPassed);

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
    // (depending on dumb-awareness), but because there is no other concurrent phase,
    // we measure it as a sequential activity to put it on the timeline and make clear what's going on the end (avoid last "unknown" phase)
    Activity dumbAwareActivity = StartUpMeasurer.startMainActivity(Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES);

    AtomicReference<Activity> edtActivity = new AtomicReference<>();
    AtomicBoolean uiFreezeWarned = new AtomicBoolean();
    AtomicBoolean eventAboutDumbUnawareActivities = new AtomicBoolean();

    AtomicInteger counter = new AtomicInteger();
    DumbService dumbService = DumbService.getInstance(myProject);
    StartupActivity.POST_STARTUP_ACTIVITY.processWithPluginDescriptor((extension, pluginDescriptor) -> {
      if (myProject.isDisposed()) {
        return;
      }

      if (DumbService.isDumbAware(extension)) {
        runActivity(null, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
        return;
      }

      if (edtActivity.get() == null) {
        edtActivity.set(StartUpMeasurer.startMainActivity("project post-startup edt activities"));
      }

      counter.incrementAndGet();
      runDumbUnawareActivity(dumbService, () -> {
        runActivity(uiFreezeWarned, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
        dumbUnawarePostActivitiesPassed(edtActivity, eventAboutDumbUnawareActivities, counter.decrementAndGet());
      });
    });

    dumbUnawarePostActivitiesPassed(edtActivity, eventAboutDumbUnawareActivities, counter.get());

    if (myProject.isDisposed()) {
      return;
    }

    StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(new ExtensionPointListener<StartupActivity>() {
      @Override
      public void extensionAdded(@NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (DumbService.isDumbAware(extension)) {
          runActivity(new AtomicBoolean(), extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
        }
        else {
          runDumbUnawareActivity(DumbService.getInstance(myProject), () -> {
            runActivity(null, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
          });
        }
      }
    }, myProject);

    runActivities(myDumbAwarePostStartupActivities, null, null);
    dumbAwareActivity.end();
    snapshot.logResponsivenessSinceCreation("Post-startup activities under progress");

    runDumbUnawarePostStartupActivitiesRegisteredDynamically();
  }

  private static void dumbUnawarePostActivitiesPassed(@NotNull AtomicReference<Activity> edtActivity,
                                                      @NotNull AtomicBoolean eventAboutDumbUnawareActivities,
                                                      int count) {
    if (count != 0) {
      return;
    }

    Activity activity = edtActivity.getAndSet(null);
    if (activity != null) {
      activity.end();
    }

    if (eventAboutDumbUnawareActivities.compareAndSet(false, true)) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLoadListener.TOPIC)
        .dumbUnawarePostStartUpActivitiesPassed();
    }
  }

  private void runActivity(@Nullable AtomicBoolean uiFreezeWarned, @NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor, @Nullable ProgressIndicator indicator) {
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
    if (uiFreezeWarned != null && duration > EDT_WARN_THRESHOLD_IN_NANO) {
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

  @TestOnly
  public final void runPostStartupActivitiesRegisteredDynamically() {
    if (postStartupActivitiesPassed) {
      return;
    }

    runActivities(myDumbAwarePostStartupActivities, null, Activities.PROJECT_DUMB_POST_STARTUP);
    runDumbUnawarePostStartupActivitiesRegisteredDynamically();
  }

  private void runDumbUnawarePostStartupActivitiesRegisteredDynamically() {
    DumbService dumbService = DumbService.getInstance(myProject);
    runDumbUnawareActivity(dumbService, new Runnable() {
      @Override
      public void run() {
        // todo should it be moved out of EDT? Not clear, do we really have a lot of such activities
        // myDumbAwarePostStartupActivities might be non-empty if new activities were registered during dumb mode
        runActivities(myDumbAwarePostStartupActivities, null, Activities.PROJECT_DUMB_POST_STARTUP);

        while (true) {
          List<Runnable> dumbUnaware = takeDumbUnawareStartupActivities();
          if (dumbUnaware.isEmpty()) {
            break;
          }

          // queue each activity in smart mode separately so that if one of them starts the dumb mode, the next ones just wait for it to finish
          for (Runnable activity : dumbUnaware) {
            runDumbUnawareActivity(dumbService, () -> runActivity(activity));
          }
        }

        if (dumbService.isDumb()) {
          // return here later to process newly submitted activities (if any) and set myPostStartupActivitiesPassed
          DumbService.getInstance(myProject).unsafeRunWhenSmart(this);
        }
        else {
          postStartupActivitiesPassed = true;
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

  private void runActivities(@NotNull Deque<? extends Runnable> activities, @Nullable ProgressIndicator indicator, @Nullable String activityName) {
    if (activities.isEmpty()) {
      return;
    }

    Activity activity = activityName == null ? null : StartUpMeasurer.startMainActivity(activityName);

    while (true) {
      Runnable runnable;
      synchronized (myLock) {
        runnable = activities.pollFirst();
      }

      if (indicator != null) {
        indicator.checkCanceled();
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

    if (activity != null) {
      activity.end();
    }
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
    checkNonDefaultProject();

    GuiUtils.invokeLaterIfNeeded(() -> {
      // in tests that simulate project opening, post-startup activities could have been run already
      // then we should act as if the project was initialized
      if (myStartupActivitiesPassed && (myProject.isOpen() || myProject.isDefault() || (postStartupActivitiesPassed && ApplicationManager.getApplication().isUnitTestMode()))) {
        action.run();
        return;
      }

      registerPostStartupDumbAwareActivity(() -> {
        if (DumbService.isDumbAware(action)) {
          runActivity(action);
        }
        else {
          runDumbUnawareActivity(DumbService.getInstance(myProject), action);
        }
      });
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }

  @Override
  public void runAfterOpened(@NotNull Runnable runnable) {
    checkNonDefaultProject();

    if (postStartupActivitiesPassed) {
      runnable.run();
    }
    else {
      synchronized (myLock) {
        if (postStartupActivitiesPassed) {
          runnable.run();
          return;
        }

        myDumbAwarePostStartupActivities.add(runnable);
      }
    }
  }

  private void runDumbUnawareActivity(@NotNull DumbService dumbService, @NotNull Runnable action) {
    GuiUtils.invokeLaterIfNeeded(() -> {
      dumbService.unsafeRunWhenSmart(action);
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }

  @TestOnly
  public synchronized void prepareForNextTest() {
    synchronized (myLock) {
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
      }
    }
    finally {
      prepareForNextTest();
    }
  }
}