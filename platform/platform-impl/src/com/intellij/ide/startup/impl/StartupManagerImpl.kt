// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.impl

import com.intellij.diagnostic.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
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
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

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
    @VisibleForTesting
    fun addActivityEpListener(project: Project) {
      StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<StartupActivity?> {
        override fun extensionAdded(activity: StartupActivity, descriptor: PluginDescriptor) {
          val startupManager = getInstance(project) as StartupManagerImpl
          val pluginId = descriptor.pluginId

          if (DumbService.isDumbAware(activity)) {
            AppExecutorUtil.getAppExecutorService().execute {
              if (!project.isDisposed) {
                BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) {
                  startupManager.runActivityAndMeasureDuration(
                    activity,
                    pluginId,
                    ProgressManager.getInstance().progressIndicator,
                  )
                }
              }
            }
          }
          else {
            DumbService.getInstance(project).unsafeRunWhenSmart {
              startupManager.runActivityAndMeasureDuration(activity, pluginId)
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

  override fun postStartupActivityPassed() =
    when (postStartupActivitiesPassed) {
      ALL_PASSED -> true
      -1 -> throw RuntimeException("Aborted; check the log for a reason")
      else -> false
    }

  override fun getAllActivitiesPassedFuture() = allActivitiesPassed

  fun projectOpened(indicator: ProgressIndicator?) {
    val app = ApplicationManager.getApplication()
    if (indicator != null && app.isInternal) {
      indicator.text = IdeBundle.message("startup.indicator.text.running.startup.activities")
    }

    // see https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity
    LOG.assertTrue(!isStartupActivitiesPassed)
    runActivity("project startup") {
      runActivities(startupActivities, indicator = indicator)
      val extensionPoint = (app.extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint<StartupActivity>("com.intellij.startupActivity")

      // use processImplementations to not even create extension if not allow-listed
      val extensionPointName = extensionPoint.name
      extensionPoint.processImplementations(true) { supplier, descriptor ->
        executeActivityFromExtensionPoint(descriptor, extensionPointName, supplier, indicator)
      }
      isStartupActivitiesPassed = true
    }

    indicator?.checkCanceled()

    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.PROJECT_OPENED)  // opened on startup
    StartUpMeasurer.compareAndSetCurrentState(LoadingState.APP_STARTED, LoadingState.PROJECT_OPENED)        // opened from the welcome screen

    if (app.isUnitTestMode && !app.isDispatchThread) {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) { runPostStartupActivities() }
    }
    else {
      ForkJoinPool.commonPool().execute {
        if (!project.isDisposed) {
          try {
            BackgroundTaskUtil.runUnderDisposeAwareIndicator(project) { runPostStartupActivities() }
          }
          catch (ignore: ProcessCanceledException) { }
        }
      }
      if (app.isUnitTestMode) {
        LOG.assertTrue(app.isDispatchThread)
        @Suppress("TestOnlyProblems")
        waitAndProcessInvocationEventsInIdeEventQueue(this)
      }
    }
  }

  private fun executeActivityFromExtensionPoint(
    descriptor: PluginDescriptor,
    extensionPointName: String,
    supplier: Supplier<out StartupActivity?>,
    indicator: ProgressIndicator?
  ) {
    if (project.isDisposed) {
      return
    }

    val pluginId = descriptor.pluginId
    if (pluginId != PluginManagerCore.CORE_ID
        && pluginId != PluginManagerCore.JAVA_PLUGIN_ID
        && pluginId.idString != "com.jetbrains.performancePlugin"
        && pluginId.idString != "com.intellij.kotlinNative.platformDeps") {
      LOG.error("Only bundled plugin can define $extensionPointName: $descriptor")
      return
    }

    indicator?.checkCanceled()
    supplier.get()?.let {
      runActivityAndMeasureDuration(it, pluginId, indicator)
    }
  }

  // Must be executed in a pooled thread outside of project loading modal task. The only exclusion - test mode.
  private fun runPostStartupActivities() {
    try {
      LOG.assertTrue(isStartupActivitiesPassed)
      val snapshot = PerformanceWatcher.takeSnapshot()
      // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
      // (depending on dumb-awareness), but because there is no other concurrent phase, we measure it as a sequential activity
      // to put it on the timeline and make clear what's going at the end (avoiding the last "unknown" phase)
      val dumbAwareActivity = StartUpMeasurer.startActivity(StartUpMeasurer.Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES)
      val edtActivity = AtomicReference<Activity?>()
      val uiFreezeWarned = AtomicBoolean()
      val counter = AtomicInteger()
      val dumbService = DumbService.getInstance(project)
      StartupActivity.POST_STARTUP_ACTIVITY.processWithPluginDescriptor { extension: StartupActivity, pluginDescriptor: PluginDescriptor ->
        if (project.isDisposed) {
          return@processWithPluginDescriptor
        }
        if (DumbService.isDumbAware(extension)) {
          dumbService.runWithWaitForSmartModeDisabled {
            runActivityAndMeasureDuration(extension, pluginDescriptor.pluginId)
          }
          return@processWithPluginDescriptor
        }
        if (edtActivity.get() == null) {
          edtActivity.set(StartUpMeasurer.startActivity("project post-startup edt activities"))
        }
        // DumbService.unsafeRunWhenSmart throws an assertion in LightEdit mode, see LightEditDumbService.unsafeRunWhenSmart
        if (!LightEdit.owns(project)) {
          counter.incrementAndGet()
          dumbService.unsafeRunWhenSmart {
            val duration = runActivityAndMeasureDuration(extension, pluginDescriptor.pluginId)
            if (duration > EDT_WARN_THRESHOLD_IN_NANO) {
              reportUiFreeze(uiFreezeWarned)
            }

            dumbUnawarePostActivitiesPassed(edtActivity, counter.decrementAndGet())
          }
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
        addActivityEpListener(project)
      }
    }
    catch (t: Throwable) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        postStartupActivitiesPassed = -1
      }
      else throw t
    }
  }

  private fun runActivityAndMeasureDuration(
    activity: StartupActivity,
    pluginId: PluginId,
    indicator: ProgressIndicator? = ProgressIndicatorProvider.getGlobalProgressIndicator(),
  ): Long {
    indicator?.pushState()
    val startTime = StartUpMeasurer.getCurrentTime()
    try {
      runStartupActivity(activity)
    }
    catch (e: Throwable) {
      if (e is ControlFlowException) throw e
      LOG.error(e)
    }
    finally {
      indicator?.popState()
    }

    return addCompletedActivity(
      startTime,
      activity.javaClass,
      pluginId,
    )
  }

  private fun runStartupActivity(activity: StartupActivity) {
    if (project !is LightEditCompatible || activity is LightEditCompatible) {
      activity.runActivity(project)
    }
  }

  private fun runPostStartupActivitiesRegisteredDynamically() {
    runActivities(postStartupActivities, activityName = "project post-startup")
    postStartupActivitiesPassed = DUMB_AWARE_PASSED
    if (LightEdit.owns(project)) {
      postStartupActivitiesPassed = ALL_PASSED
      allActivitiesPassed.complete(null)
    }
    else {
      DumbService.getInstance(project).unsafeRunWhenSmart(object : Runnable {
        override fun run() {
          synchronized(lock) {
            if (postStartupActivities.isEmpty()) {
              postStartupActivitiesPassed = ALL_PASSED
              allActivitiesPassed.complete(null)
              return
            }
          }

          runActivities(postStartupActivities)
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
  }

  private fun runActivities(activities: Deque<Runnable>, activityName: String? = null, indicator: ProgressIndicator? = null) {
    synchronized(lock) {
      if (activities.isEmpty()) {
        return
      }
    }

    val activity = activityName?.let {
      StartUpMeasurer.startActivity(it)
    }
    while (true) {
      val runnable = synchronized(lock) {
        activities.pollFirst()
      } ?: break
      indicator?.checkCanceled()

      val startTime = StartUpMeasurer.getCurrentTime()
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

      val runnableClass = runnable.javaClass
      addCompletedActivity(
        startTime = startTime,
        runnableClass = runnableClass,
        pluginId = (runnableClass.classLoader as? PluginClassLoader)?.pluginId ?: PluginManagerCore.CORE_ID,
      )
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
    scheduledFuture?.cancel(false)
    scheduledFuture = null
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
      runAfterOpened { ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, project.disposed, action) }
    }
    else if (!LightEdit.owns(project)) {
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

private fun addCompletedActivity(startTime: Long, runnableClass: Class<*>, pluginId: PluginId): Long {
  return StartUpMeasurer.addCompletedActivity(
    startTime,
    runnableClass,
    ActivityCategory.POST_STARTUP_ACTIVITY,
    pluginId.idString,
    StartUpMeasurer.MEASURE_THRESHOLD,
  )
}

private fun dumbUnawarePostActivitiesPassed(edtActivity: AtomicReference<Activity?>, count: Int) {
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
