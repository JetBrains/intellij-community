// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup.impl

import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.waitAndProcessInvocationEventsInIdeEventQueue
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ModalityUiUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<StartupManagerImpl>()

/**
 * Acts as [StartupActivity.POST_STARTUP_ACTIVITY], but executed with 5 seconds delay after project opening.
 */
private val BACKGROUND_POST_STARTUP_ACTIVITY = ExtensionPointName<StartupActivity.Background>("com.intellij.backgroundPostStartupActivity")
private val EDT_WARN_THRESHOLD_IN_NANO = TimeUnit.MILLISECONDS.toNanos(100)
private const val DUMB_AWARE_PASSED = 1
private const val ALL_PASSED = 2

@ApiStatus.Internal
open class StartupManagerImpl(private val project: Project) : StartupManagerEx(), Disposable {
  companion object {
    @TestOnly
    fun addActivityEpListener(project: Project) {
      StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<StartupActivity?> {
        override fun extensionAdded(extension: StartupActivity, pluginDescriptor: PluginDescriptor) {
          val startupManager = getInstance(project) as StartupManagerImpl
          if (DumbService.isDumbAware(extension)) {
            AppExecutorUtil.getAppExecutorService().execute {
              if (!project.isDisposed) {
                BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) {
                  startupManager.runActivity(null, extension, pluginDescriptor, ProgressManager.getInstance().progressIndicator)
                }
              }
            }
          }
          else {
            DumbService.getInstance(project).unsafeRunWhenSmart {
              startupManager.runActivity(null, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator())
            }
          }
        }
      }, project)
    }
  }

  private val lock = Any()
  private val startupActivities = ArrayDeque<Runnable>()
  private val postStartupActivities = ArrayDeque<Runnable>()

  @MagicConstant(intValues = [0, DUMB_AWARE_PASSED.toLong(), ALL_PASSED.toLong()])
  @Volatile
  private var postStartupActivitiesPassed = 0
  private val allActivitiesPassed = CompletableFuture<Any?>()

  @Volatile
  private var isStartupActivitiesPassed = false

  private fun checkNonDefaultProject() {
    LOG.assertTrue(!project.isDefault, "Please don't register startup activities for the default project: they won't ever be run")
  }

  override fun registerStartupActivity(runnable: Runnable) {
    checkNonDefaultProject()
    LOG.assertTrue(!isStartupActivitiesPassed, "Registering startup activity that will never be run")
    synchronized(lock) {
      startupActivities.add(runnable)
    }
  }

  override fun registerPostStartupActivity(runnable: Runnable) {
    if (DumbService.isDumbAware(runnable)) {
      runAfterOpened(runnable)
      return
    }

    checkNonDefaultProject()
    checkThatPostActivitiesNotPassed()
    LOG.warn("Activities registered via registerPostStartupActivity must be dumb-aware: $runnable")
    synchronized(lock) {
      checkThatPostActivitiesNotPassed()
      postStartupActivities.add(DumbAwareRunnable {
        DumbService.getInstance(project).unsafeRunWhenSmart(runnable)
      })
    }
  }

  private fun checkThatPostActivitiesNotPassed() {
    if (postStartupActivityPassed()) {
      LOG.error("Registering post-startup activity that will never be run (" +
                " disposed=${project.isDisposed}" +
                ", open=${project.isOpen}" +
                ", passed=$isStartupActivitiesPassed"
                + ")")
    }
  }

  override fun startupActivityPassed() = isStartupActivitiesPassed

  override fun postStartupActivityPassed() = postStartupActivitiesPassed == ALL_PASSED

  override fun getAllActivitiesPassedFuture() = allActivitiesPassed

  fun projectOpened(indicator: ProgressIndicator?) {
    val app = ApplicationManager.getApplication()
    if (indicator != null && app.isInternal) {
      indicator.text = IdeBundle.message("startup.indicator.text.running.startup.activities")
    }

    // see https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity
    LOG.assertTrue(!isStartupActivitiesPassed)
    runActivity("project startup") {
      runActivities(startupActivities, indicator, null)
      val area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
      executeActivitiesFromExtensionPoint(indicator, area.getExtensionPoint("com.intellij.startupActivity"))
      isStartupActivitiesPassed = true
    }

    indicator?.checkCanceled()
    val phase = if (DumbService.isDumb(project)) LoadingState.PROJECT_OPENED else LoadingState.INDEXING_FINISHED
    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, phase)
    if (app.isUnitTestMode && !app.isDispatchThread) {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) { runPostStartupActivities() }
    }
    else {
      ForkJoinPool.commonPool().execute {
        if (!project.isDisposed) {
          BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) { runPostStartupActivities() }
        }
      }
      if (app.isUnitTestMode) {
        LOG.assertTrue(app.isDispatchThread)
        waitAndProcessInvocationEventsInIdeEventQueue(this)
      }
    }
  }

  private fun executeActivitiesFromExtensionPoint(indicator: ProgressIndicator?, extensionPoint: ExtensionPointImpl<StartupActivity>) {
    // use processImplementations to not even create extension if not white-listed
    extensionPoint.processImplementations( /* shouldBeSorted = */true) { supplier, pluginDescriptor ->
      if (project.isDisposed) {
        return@processImplementations
      }

      val id = pluginDescriptor.pluginId
      if (!(id == PluginManagerCore.CORE_ID ||
            id == PluginManagerCore.JAVA_PLUGIN_ID ||
            id.idString == "com.jetbrains.performancePlugin" ||
            id.idString == "com.intellij.kotlinNative.platformDeps")) {
        LOG.error("Only bundled plugin can define ${extensionPoint.name}: $pluginDescriptor")
        return@processImplementations
      }

      indicator?.checkCanceled()
      runActivity(null, supplier.get() ?: return@processImplementations, pluginDescriptor, indicator)
    }
  }

  // Must be executed in a pooled thread outside of project loading modal task. The only exclusion - test mode.
  private fun runPostStartupActivities() {
    LOG.assertTrue(isStartupActivitiesPassed)
    val snapshot = PerformanceWatcher.takeSnapshot()
    // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
    // (depending on dumb-awareness), but because there is no other concurrent phase,ur
    // we measure it as a sequential activity to put it on the timeline and make clear what's going on the end (avoid last "unknown" phase)
    val dumbAwareActivity = startActivity(StartUpMeasurer.Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES)
    val edtActivity = AtomicReference<Activity?>()
    val uiFreezeWarned = AtomicBoolean()
    val counter = AtomicInteger()
    val dumbService = DumbService.getInstance(project)
    StartupActivity.POST_STARTUP_ACTIVITY.processWithPluginDescriptor { extension: StartupActivity, pluginDescriptor: PluginDescriptor ->
      if (project.isDisposed) {
        return@processWithPluginDescriptor
      }
      if (DumbService.isDumbAware(extension)) {
        runActivity(null, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator())
        return@processWithPluginDescriptor
      }
      if (edtActivity.get() == null) {
        edtActivity.set(startActivity("project post-startup edt activities"))
      }
      counter.incrementAndGet()
      dumbService.unsafeRunWhenSmart {
        runActivity(uiFreezeWarned, extension, pluginDescriptor, ProgressIndicatorProvider.getGlobalProgressIndicator())
        dumbUnawarePostActivitiesPassed(edtActivity, counter.decrementAndGet())
      }
    }
    dumbUnawarePostActivitiesPassed(edtActivity, counter.get())
    if (project.isDisposed) {
      return
    }
    runPostStartupActivitiesRegisteredDynamically()
    dumbAwareActivity.end()
    snapshot.logResponsivenessSinceCreation("Post-startup activities under progress")
    if (!project.isDisposed && !ApplicationManager.getApplication().isUnitTestMode) {
      scheduleBackgroundPostStartupActivities()
      @Suppress("TestOnlyProblems")
      addActivityEpListener(project)
    }
  }

  private fun runActivity(uiFreezeWarned: AtomicBoolean?,
                          extension: StartupActivity,
                          pluginDescriptor: PluginDescriptor,
                          indicator: ProgressIndicator?) {
    indicator?.pushState()
    val startTime = StartUpMeasurer.getCurrentTime()
    try {
      runStartupActivity(extension)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    finally {
      indicator?.popState()
    }
    val pluginId = pluginDescriptor.pluginId.idString
    val duration = StartUpMeasurer.addCompletedActivity(startTime, extension.javaClass, ActivityCategory.POST_STARTUP_ACTIVITY, pluginId,
      StartUpMeasurer.MEASURE_THRESHOLD)
    if (uiFreezeWarned != null && duration > EDT_WARN_THRESHOLD_IN_NANO) {
      reportUiFreeze(uiFreezeWarned)
    }
  }

  private fun runStartupActivity(activity: StartupActivity) {
    if (project !is LightEditCompatible || activity is LightEditCompatible) {
      activity.runActivity(project)
    }
  }

  private fun runPostStartupActivitiesRegisteredDynamically() {
    runActivities(postStartupActivities, null, "project post-startup")
    postStartupActivitiesPassed = DUMB_AWARE_PASSED
    DumbService.getInstance(project).unsafeRunWhenSmart(object : Runnable {
      override fun run() {
        synchronized(lock) {
          if (postStartupActivities.isEmpty()) {
            postStartupActivitiesPassed = ALL_PASSED
            allActivitiesPassed.complete(null)
            return
          }
        }

        runActivities(postStartupActivities, null, null)
        val dumbService = DumbService.getInstance(project)
        if (dumbService.isDumb) {
          // return here later to process newly submitted activities (if any) and set postStartupActivitiesPassed
          dumbService.unsafeRunWhenSmart(this)
        }
        else {
          postStartupActivitiesPassed = ALL_PASSED
          allActivitiesPassed.complete(null)
        }
      }
    })
  }

  private fun runActivities(activities: Deque<Runnable>, indicator: ProgressIndicator?, activityName: String?) {
    synchronized(lock) {
      if (activities.isEmpty()) {
        return
      }
    }

    val activity = if (activityName == null) null else startActivity(activityName)
    while (true) {
      val runnable = synchronized(lock) { activities.pollFirst() } ?: break
      indicator?.checkCanceled()

      val startTime = StartUpMeasurer.getCurrentTime()
      val loader = runnable.javaClass.classLoader
      val pluginId = if (loader is PluginClassLoader) loader.pluginId.idString else PluginManagerCore.CORE_ID.idString
      ProgressManager.checkCanceled()
      try {
        runnable.run()
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      StartUpMeasurer.addCompletedActivity(startTime, runnable.javaClass, ActivityCategory.POST_STARTUP_ACTIVITY, pluginId,
        StartUpMeasurer.MEASURE_THRESHOLD)
    }
    activity?.end()
  }

  private var scheduledFuture: ScheduledFuture<*>? = null
  private fun scheduleBackgroundPostStartupActivities() {
    scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().schedule({
      scheduledFuture = null
      if (project.isDisposed) {
        return@schedule
      }

      val startTimeNano = System.nanoTime()
      // read action - dynamic plugin loading executed as a write action
      val activities = ReadAction.compute<List<StartupActivity.Background>, RuntimeException> {
        BACKGROUND_POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<StartupActivity.Background?> {
          override fun extensionAdded(extension: StartupActivity.Background, pluginDescriptor: PluginDescriptor) {
            AppExecutorUtil.getAppScheduledExecutorService().execute { runBackgroundPostStartupActivities(listOf(extension)) }
          }
        }, project)
        BACKGROUND_POST_STARTUP_ACTIVITY.extensionList
      }
      runBackgroundPostStartupActivities(activities)
      LOG.debug {
        "Background post-startup activities done in ${TimeoutUtil.getDurationMillis(startTimeNano)}ms"
      }
    }, Registry.intValue("ide.background.post.startup.activity.delay").toLong(), TimeUnit.MILLISECONDS)
  }

  override fun dispose() {
    val future = scheduledFuture
    if (future != null) {
      scheduledFuture = null
      future.cancel(false)
    }
  }

  private fun runBackgroundPostStartupActivities(activities: List<StartupActivity.Background>) {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) {
      for (activity in activities) {
        ProgressManager.checkCanceled()
        if (project.isDisposed) {
          return@runUnderDisposeAwareIndicator
        }
        runStartupActivity(activity)
      }
    }
  }

  override fun runWhenProjectIsInitialized(action: Runnable) {
    if (DumbService.isDumbAware(action)) {
      runAfterOpened {
        ModalityUiUtil.invokeLaterIfNeeded(action, ModalityState.NON_MODAL, project.disposed)
      }
    }
    else {
      runAfterOpened { DumbService.getInstance(project).unsafeRunWhenSmart(action) }
    }
  }

  override fun runAfterOpened(runnable: Runnable) {
    checkNonDefaultProject()
    if (postStartupActivitiesPassed < DUMB_AWARE_PASSED) {
      synchronized(lock) {
        if (postStartupActivitiesPassed < DUMB_AWARE_PASSED) {
          postStartupActivities.add(runnable)
          return
        }
      }
    }
    runnable.run()
  }

  @TestOnly
  @Synchronized
  fun prepareForNextTest() {
    synchronized(lock) {
      startupActivities.clear()
      postStartupActivities.clear()
    }
  }

  @TestOnly
  @Synchronized
  fun checkCleared() {
    try {
      synchronized(lock) {
        assert(startupActivities.isEmpty()) { "Activities: $startupActivities" }
        assert(postStartupActivities.isEmpty()) { "DumbAware Post Activities: $postStartupActivities" }
      }
    }
    finally {
      prepareForNextTest()
    }
  }
}

private fun dumbUnawarePostActivitiesPassed(edtActivity: AtomicReference<out Activity?>, count: Int) {
  if (count == 0) {
    edtActivity.getAndSet(null)?.end()
  }
}

private fun reportUiFreeze(uiFreezeWarned: AtomicBoolean) {
  val app = ApplicationManager.getApplication()
  if (!app.isUnitTestMode && app.isDispatchThread && uiFreezeWarned.compareAndSet(false, true)) {
    LOG.info("Some post-startup activities freeze UI for noticeable time. " +
             "Please consider making them DumbAware to run them in background under modal progress," +
             " or just making them faster to speed up project opening.")
  }
}
