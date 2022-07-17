// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION")

package com.intellij.ide.startup.impl

import com.intellij.diagnostic.*
import com.intellij.diagnostic.opentelemetry.TraceManager
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.idea.processExtensions
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.isCorePlugin
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.ModalityUiUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.InvocationEvent
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private val LOG = logger<StartupManagerImpl>()
private val tracer by lazy { TraceManager.getTracer("startupManager") }

/**
 * Acts as [StartupActivity.POST_STARTUP_ACTIVITY], but executed with 5 seconds delay after project opening.
 */
private val BACKGROUND_POST_STARTUP_ACTIVITY = ExtensionPointName<StartupActivity>("com.intellij.backgroundPostStartupActivity")
private val EDT_WARN_THRESHOLD_IN_NANO = TimeUnit.MILLISECONDS.toNanos(100)
private const val DUMB_AWARE_PASSED = 1
private const val ALL_PASSED = 2

@ApiStatus.Internal
open class StartupManagerImpl(private val project: Project) : StartupManagerEx() {
  companion object {
    @VisibleForTesting
    fun addActivityEpListener(project: Project) {
      StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<StartupActivity?> {
        override fun extensionAdded(activity: StartupActivity, descriptor: PluginDescriptor) {
          val startupManager = getInstance(project) as StartupManagerImpl
          val pluginId = descriptor.pluginId
          @Suppress("SSBasedInspection")
          if (activity is DumbAware) {
            project.coroutineScope.launch {
              if (activity is ProjectPostStartupActivity) {
                DumbService.getInstance(project).runWithWaitForSmartModeDisabled().use {
                  startupManager.runActivityAndMeasureDuration(activity, descriptor.pluginId, project !is LightEditCompatible)
                }
              }
              else {
                startupManager.runActivityAndMeasureDuration(activity, pluginId)
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
  private val allActivitiesPassed = CompletableDeferred<Any?>()

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
    Span.current().addEvent("register startup activity", Attributes.of(AttributeKey.stringKey("runnable"), runnable.toString()))

    @Suppress("SSBasedInspection")
    if (runnable is DumbAware) {
      runAfterOpened(runnable)
    }
    else {
      LOG.error("Activities registered via registerPostStartupActivity must be dumb-aware: $runnable")
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

  override fun postStartupActivityPassed(): Boolean {
    return when (postStartupActivitiesPassed) {
      ALL_PASSED -> true
      -1 -> throw RuntimeException("Aborted; check the log for a reason")
      else -> false
    }
  }

  override fun getAllActivitiesPassedFuture() = allActivitiesPassed

  suspend fun projectOpened(indicator: ProgressIndicator?) {
    val app = ApplicationManager.getApplication()
    // see https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity
    LOG.assertTrue(!isStartupActivitiesPassed)
    runActivity("project startup") {
      tracer.spanBuilder("run startup activities").useWithScope {
        runStartUpActivities(indicator, app)
        isStartupActivitiesPassed = true
      }
    }

    // opened on startup
    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.PROJECT_OPENED)
    // opened from the welcome screen
    StartUpMeasurer.compareAndSetCurrentState(LoadingState.APP_STARTED, LoadingState.PROJECT_OPENED)

    coroutineContext.ensureActive()

    if (app.isUnitTestMode && !app.isDispatchThread) {
      runPostStartupActivities()
    }
    else {
      // doesn't block project opening
      project.coroutineScope.launch {
        runPostStartupActivities()
      }
      if (app.isUnitTestMode) {
        LOG.assertTrue(app.isDispatchThread)
        @Suppress("TestOnlyProblems")
        waitAndProcessInvocationEventsInIdeEventQueue(this)
      }
    }
  }

  private suspend fun runStartUpActivities(indicator: ProgressIndicator?, app: Application) {
    runActivities(startupActivities)
    val extensionPoint = (app.extensionArea as ExtensionsAreaImpl).getExtensionPoint<StartupActivity>("com.intellij.startupActivity")

    // do not create extension if not allow-listed
    for (adapter in extensionPoint.sortedAdapters) {
      coroutineContext.ensureActive()

      val pluginId = adapter.pluginDescriptor.pluginId
      if (!isCorePlugin(adapter.pluginDescriptor) && pluginId.idString != "com.jetbrains.performancePlugin") {
        LOG.error("Only bundled plugin can define ${extensionPoint.name}: ${adapter.pluginDescriptor}")
        continue
      }

      val activity = adapter.createInstance<InitProjectActivity>(project) ?: continue
      indicator?.pushState()
      val startTime = StartUpMeasurer.getCurrentTime()
      try {
        tracer.spanBuilder("run activity")
          .setAttribute(AttributeKey.stringKey("class"), activity.javaClass.name)
          .setAttribute(AttributeKey.stringKey("plugin"), pluginId.idString)
          .useWithScope {
            if (project !is LightEditCompatible || activity is LightEditCompatible) {
              activity.run(project)
            }
          }
      }
      finally {
        indicator?.popState()
      }

      addCompletedActivity(startTime = startTime, runnableClass = activity.javaClass, pluginId = pluginId)
    }
  }

  // Must be executed in a pooled thread outside of project loading modal task. The only exclusion - test mode.
  private suspend fun runPostStartupActivities() {
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
      val isProjectLightEditCompatible = project is LightEditCompatible
      StartupActivity.POST_STARTUP_ACTIVITY.processExtensions { activity, pluginDescriptor ->
        if (activity is ProjectPostStartupActivity) {
          dumbService.runWithWaitForSmartModeDisabled().use {
            runActivityAndMeasureDuration(activity, pluginDescriptor.pluginId, isProjectLightEditCompatible)
          }
          return@processExtensions
        }
        else {
          @Suppress("SSBasedInspection")
          if (activity is DumbAware) {
            dumbService.runWithWaitForSmartModeDisabled().use {
              runActivityAndMeasureDuration(activity, pluginDescriptor.pluginId)
            }
            return@processExtensions
          }
          else {
            if (edtActivity.get() == null) {
              edtActivity.set(StartUpMeasurer.startActivity("project post-startup edt activities"))
            }

            // DumbService.unsafeRunWhenSmart throws an assertion in LightEdit mode, see LightEditDumbService.unsafeRunWhenSmart
            if (!isProjectLightEditCompatible) {
              counter.incrementAndGet()
              val traceContext = Context.current()
              dumbService.unsafeRunWhenSmart {
                traceContext.makeCurrent()
                val duration = runActivityAndMeasureDuration(activity, pluginDescriptor.pluginId)
                if (duration > EDT_WARN_THRESHOLD_IN_NANO) {
                  reportUiFreeze(uiFreezeWarned)
                }
                dumbUnawarePostActivitiesPassed(edtActivity, counter.decrementAndGet())
              }
            }
          }
        }
      }

      dumbUnawarePostActivitiesPassed(edtActivity, counter.get())

      runPostStartupActivitiesRegisteredDynamically()
      dumbAwareActivity.end()
      snapshot.logResponsivenessSinceCreation("Post-startup activities under progress")

      coroutineContext.ensureActive()

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        scheduleBackgroundPostStartupActivities()
        addActivityEpListener(project)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        postStartupActivitiesPassed = -1
      }
      else {
        throw e
      }
    }
  }

  private fun runActivityAndMeasureDuration(activity: StartupActivity, pluginId: PluginId): Long {
    val startTime = StartUpMeasurer.getCurrentTime()
    try {
      tracer.spanBuilder("run activity")
        .setAttribute(AttributeKey.stringKey("class"), activity.javaClass.name)
        .setAttribute(AttributeKey.stringKey("plugin"), pluginId.idString)
        .useWithScope {
          if (project !is LightEditCompatible || activity is LightEditCompatible) {
            activity.runActivity(project)
          }
        }
    }
    catch (e: Throwable) {
      if (e is ControlFlowException || e is CancellationException) {
        throw e
      }
      LOG.error(e)
    }

    return addCompletedActivity(startTime = startTime, runnableClass = activity.javaClass, pluginId = pluginId)
  }

  private suspend fun runActivityAndMeasureDuration(
    activity: ProjectPostStartupActivity,
    pluginId: PluginId,
    isProjectLightEditCompatible: Boolean
  ): Long {
    val startTime = StartUpMeasurer.getCurrentTime()
    tracer.spanBuilder("run activity")
      .setAttribute(AttributeKey.stringKey("class"), activity.javaClass.name)
      .setAttribute(AttributeKey.stringKey("plugin"), pluginId.idString)
      .useWithScope {
        if (!isProjectLightEditCompatible || activity is LightEditCompatible) {
          activity.execute(project)
        }
      }
    return addCompletedActivity(startTime = startTime, runnableClass = activity.javaClass, pluginId = pluginId)
  }

  private suspend fun runPostStartupActivitiesRegisteredDynamically() {
    tracer.spanBuilder("run post-startup dynamically registered activities").useWithScope {
      runActivities(postStartupActivities, activityName = "project post-startup")
    }

    postStartupActivitiesPassed = DUMB_AWARE_PASSED
    postStartupActivitiesPassed = ALL_PASSED
    allActivitiesPassed.complete(value = null)
  }

  private suspend fun runActivities(activities: Deque<Runnable>, activityName: String? = null) {
    synchronized(lock) {
      if (activities.isEmpty()) {
        return
      }
    }

    val activity = activityName?.let {
      StartUpMeasurer.startActivity(it)
    }
    while (true) {
      coroutineContext.ensureActive()

      val runnable = synchronized(lock, activities::pollFirst) ?: break
      val startTime = StartUpMeasurer.getCurrentTime()
      val runnableClass = runnable.javaClass
      val pluginId = (runnableClass.classLoader as? PluginAwareClassLoader)?.pluginId ?: PluginManagerCore.CORE_ID
      try {
        tracer.spanBuilder("run activity")
          .setAttribute(AttributeKey.stringKey("class"), runnableClass.name)
          .setAttribute(AttributeKey.stringKey("plugin"), pluginId.idString)
          .useWithScope {
            runnable.run()
          }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      addCompletedActivity(startTime = startTime, runnableClass = runnableClass, pluginId = pluginId)
    }
    activity?.end()
  }

  private fun scheduleBackgroundPostStartupActivities() {
    project.coroutineScope.launch {
      delay(Registry.intValue("ide.background.post.startup.activity.delay", 5_000).toLong())
      // read action - dynamic plugin loading executed as a write action
      val activities = readAction {
        BACKGROUND_POST_STARTUP_ACTIVITY.addExtensionPointListener(
          object : ExtensionPointListener<StartupActivity> {
            override fun extensionAdded(extension: StartupActivity, pluginDescriptor: PluginDescriptor) {
              project.coroutineScope.runBackgroundPostStartupActivities(listOf(extension))
            }
          }, project)
        BACKGROUND_POST_STARTUP_ACTIVITY.extensionList
      }

      if (!isActive) {
        return@launch
      }

      runBackgroundPostStartupActivities(activities)
    }
  }

  private fun CoroutineScope.runBackgroundPostStartupActivities(activities: List<StartupActivity>) {
    for (activity in activities) {
      try {
        if (project !is LightEditCompatible || activity is LightEditCompatible) {
          if (activity is ProjectPostStartupActivity) {
            launch {
              activity.execute(project)
            }
          }
          else {
            activity.runActivity(project)
          }
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: AlreadyDisposedException) {
        coroutineContext.ensureActive()
      }
      catch (e: Throwable) {
        if (e is ControlFlowException) {
          throw e
        }
        LOG.error(e)
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

// allow `invokeAndWait` inside startup activities
private suspend fun waitAndProcessInvocationEventsInIdeEventQueue(startupManager: StartupManagerImpl) {
  ApplicationManager.getApplication().assertIsDispatchThread()
  val eventQueue = IdeEventQueue.getInstance()
  if (startupManager.postStartupActivityPassed()) {
    withContext(Dispatchers.EDT) {
    }
  }
  else {
    // make sure eventQueue.nextEvent will unblock
    startupManager.runAfterOpened(DumbAwareRunnable { ApplicationManager.getApplication().invokeLater { } })
  }

  while (true) {
    val event = eventQueue.nextEvent
    if (event is InvocationEvent) {
      eventQueue.dispatchEvent(event)
    }
    if (startupManager.postStartupActivityPassed() && eventQueue.peekEvent() == null) {
      break
    }
  }
}