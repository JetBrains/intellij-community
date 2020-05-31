// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl;

import com.intellij.diagnostic.*;
import com.intellij.diagnostic.StartUpMeasurer.Activities;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;
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

  private final Deque<Runnable> startupActivities = new ArrayDeque<>();
  private final Deque<Runnable> postStartupActivities = new ArrayDeque<>();
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
      startupActivities.add(runnable);
    }
  }

  @Override
  public void registerPostStartupActivity(@NotNull Runnable runnable) {
    checkNonDefaultProject();
    checkThatPostActivitiesNotPassed();

    Runnable effectiveRunnable;
    if (DumbService.isDumbAware(runnable)) {
      effectiveRunnable = runnable;
    }
    else {
      //LOG.warn("Activities registered via registerPostStartupActivity must be dumb-aware");
      effectiveRunnable = (DumbAwareRunnable)() -> DumbService.getInstance(myProject).unsafeRunWhenSmart(runnable);
    }
    synchronized (myLock) {
      checkThatPostActivitiesNotPassed();
      postStartupActivities.add(effectiveRunnable);
    }
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

  public final @NotNull Future<?> projectOpened(@Nullable ProgressIndicator indicator) {
    if (indicator != null && ApplicationManager.getApplication().isInternal()) {
      indicator.setText(IdeBundle.message("startup.indicator.text.running.startup.activities"));
    }

    runStartUpActivities(indicator);

    if (indicator != null) {
      indicator.checkCanceled();
    }

    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, this::runPostStartupActivities);
    });

    LoadingState phase = DumbService.isDumb(myProject) ? LoadingState.PROJECT_OPENED : LoadingState.INDEXING_FINISHED;
    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, phase);
    return future;
  }

  private void runStartUpActivities(@Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!myStartupActivitiesPassed);

    Activity activity = StartUpMeasurer.startMainActivity("project startup");
    runActivities(startupActivities, indicator, null);
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

  /**
   * @deprecated Use {@link com.intellij.testFramework.PlatformTestUtil#loadAndOpenProject(Path)} in tests.
   */
  @TestOnly
  @Deprecated
  public void runStartupActivities() {
    if (!myStartupActivitiesPassed) {
      runStartUpActivities(null);
    }
  }

  // Must be executed in a pooled thread outside of project loading modal task. The only exclusion - test mode.
  private void runPostStartupActivities() {
    LOG.assertTrue(myStartupActivitiesPassed);

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
    // (depending on dumb-awareness), but because there is no other concurrent phase,
    // we measure it as a sequential activity to put it on the timeline and make clear what's going on the end (avoid last "unknown" phase)
    Activity dumbAwareActivity = StartUpMeasurer.startMainActivity(Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES);

    AtomicReference<Activity> edtActivity = new AtomicReference<>();
    AtomicBoolean uiFreezeWarned = new AtomicBoolean();

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
      dumbService.unsafeRunWhenSmart(() -> {
        runActivity(uiFreezeWarned, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
        dumbUnawarePostActivitiesPassed(edtActivity, counter.decrementAndGet());
      });
    });

    dumbUnawarePostActivitiesPassed(edtActivity, counter.get());

    if (myProject.isDisposed()) {
      return;
    }

    //noinspection TestOnlyProblems
    runPostStartupActivitiesRegisteredDynamically();
    dumbAwareActivity.end();

    snapshot.logResponsivenessSinceCreation("Post-startup activities under progress");

    if (!myProject.isDisposed() && !ApplicationManager.getApplication().isUnitTestMode()) {
      scheduleBackgroundPostStartupActivities();
    }

    StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(new ExtensionPointListener<StartupActivity>() {
      @Override
      public void extensionAdded(@NotNull StartupActivity extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (DumbService.isDumbAware(extension)) {
          runActivity(new AtomicBoolean(), extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
        }
        else {
          DumbService.getInstance(myProject).unsafeRunWhenSmart(() -> {
            runActivity(null, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator());
          });
        }
      }
    }, myProject);
  }

  private static void dumbUnawarePostActivitiesPassed(@NotNull AtomicReference<Activity> edtActivity, int count) {
    if (count != 0) {
      return;
    }

    Activity activity = edtActivity.getAndSet(null);
    if (activity != null) {
      activity.end();
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

    runActivities(postStartupActivities, null, "project post-startup");

    DumbService.getInstance(myProject).unsafeRunWhenSmart(new Runnable() {
      @Override
      public void run() {
        synchronized (myLock) {
          if (postStartupActivities.isEmpty()) {
            postStartupActivitiesPassed = true;
            return;
          }
        }

        runActivities(postStartupActivities, null, null);
        DumbService dumbService = DumbService.getInstance(myProject);
        if (dumbService.isDumb()) {
          // return here later to process newly submitted activities (if any) and set postStartupActivitiesPassed
          dumbService.unsafeRunWhenSmart(this);
        }
        else {
          postStartupActivitiesPassed = true;
        }
      }
    });
  }

  private void runActivities(@NotNull Deque<? extends Runnable> activities, @Nullable ProgressIndicator indicator, @Nullable String activityName) {
    synchronized (myLock) {
      if (activities.isEmpty()) {
        return;
      }
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

      runAfterOpened(() -> {
        if (DumbService.isDumbAware(action)) {
          runActivity(action);
        }
        else {
          DumbService.getInstance(myProject).unsafeRunWhenSmart(action);
        }
      });
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }

  @Override
  public void runAfterOpened(@NotNull Runnable runnable) {
    checkNonDefaultProject();

    if (!postStartupActivitiesPassed) {
      synchronized (myLock) {
        if (!postStartupActivitiesPassed) {
          postStartupActivities.add(runnable);
          return;
        }
      }
    }

    runnable.run();
  }

  @TestOnly
  public synchronized void prepareForNextTest() {
    synchronized (myLock) {
      startupActivities.clear();
      postStartupActivities.clear();
    }
  }

  @TestOnly
  public synchronized void checkCleared() {
    try {
      synchronized (myLock) {
        assert startupActivities.isEmpty() : "Activities: " + startupActivities;
        assert postStartupActivities.isEmpty() : "DumbAware Post Activities: " + postStartupActivities;
      }
    }
    finally {
      prepareForNextTest();
    }
  }
}