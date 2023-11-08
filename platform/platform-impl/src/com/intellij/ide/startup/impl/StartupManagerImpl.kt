// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION")

package com.intellij.ide.startup.impl

import com.intellij.diagnostic.*
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.isCorePlugin
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.ThreadingAssertions
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.InvocationEvent
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

private val LOG = logger<StartupManagerImpl>()
private val tracer by lazy { TelemetryManager.getSimpleTracer(Scope("startup")) }

/**
 * Acts as [StartupActivity.POST_STARTUP_ACTIVITY], but executed with 5-seconds delay after project opening.
 */
private val BACKGROUND_POST_STARTUP_ACTIVITY: ExtensionPointName<Any> = ExtensionPointName("com.intellij.backgroundPostStartupActivity")

private const val DUMB_AWARE_PASSED = 1
private const val ALL_PASSED = 2

@ApiStatus.Internal
open class StartupManagerImpl(private val project: Project, private val coroutineScope: CoroutineScope) : StartupManagerEx() {
  companion object {
    @VisibleForTesting
    fun addActivityEpListener(project: Project) {
      StartupActivity.POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<Any> {
        override fun extensionAdded(extension: Any, pluginDescriptor: PluginDescriptor) {
          if (project is LightEditCompatible && extension !is LightEditCompatible) {
            return
          }

          val startupManager = getInstance(project) as StartupManagerImpl
          when (extension) {
            is ProjectActivity -> {
              startupManager.coroutineScope.launch {
                extension.execute(project)
              }
            }
            is DumbAware -> {
              startupManager.coroutineScope.launch {
                @Suppress("UsagesOfObsoleteApi")
                startupManager.runOldActivity(extension as StartupActivity)
              }
            }
            else -> {
              DumbService.getInstance(project).runWhenSmart {
                @Suppress("UsagesOfObsoleteApi")
                startupManager.runOldActivity(extension as StartupActivity)
              }
            }
          }
        }
      }, project)
    }
  }

  private val lock = Any()
  private val initProjectStartupActivities = ArrayDeque<Runnable>()
  private val postStartupActivities = ArrayDeque<Runnable>()

  @Volatile
  private var freezePostStartupActivities = false

  @MagicConstant(intValues = [0, DUMB_AWARE_PASSED.toLong(), ALL_PASSED.toLong()])
  @Volatile
  private var postStartupActivitiesPassed = 0
  private val allActivitiesPassed = CompletableDeferred<Any?>()

  @Volatile
  private var isInitProjectActivitiesPassed = false

  private fun checkNonDefaultProject() {
    LOG.assertTrue(!project.isDefault, "Please don't register startup activities for the default project: they won't ever be run")
  }

  override fun registerStartupActivity(runnable: Runnable) {
    checkNonDefaultProject()
    LOG.assertTrue(!isInitProjectActivitiesPassed, "Registering startup activity that will never be run")
    synchronized(lock) {
      initProjectStartupActivities.add(runnable)
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

  override fun startupActivityPassed(): Boolean = isInitProjectActivitiesPassed

  override fun postStartupActivityPassed(): Boolean {
    return when (postStartupActivitiesPassed) {
      ALL_PASSED -> true
      -1 -> throw RuntimeException("Aborted; check the log for a reason")
      else -> false
    }
  }

  override fun getAllActivitiesPassedFuture(): CompletableDeferred<Any?> = allActivitiesPassed

  suspend fun initProject() {
    LOG.assertTrue(!isInitProjectActivitiesPassed)
    runInitProjectActivities()
    isInitProjectActivitiesPassed = true
  }

  suspend fun runPostStartupActivities() {
    // opened on startup
    LoadingState.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.PROJECT_OPENED)
    // opened from the welcome screen
    LoadingState.compareAndSetCurrentState(LoadingState.APP_STARTED, LoadingState.PROJECT_OPENED)

    coroutineContext.ensureActive()

    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) {
      if (app.isDispatchThread) {
        // doesn't block project opening
        coroutineScope.launch {
          runPostStartupActivities(async = true)
        }
        waitAndProcessInvocationEventsInIdeEventQueue(this)
      }
      else {
        runPostStartupActivities(async = false)
      }
    }
    else {
      coroutineScope.launch(tracer.span("project post-startup activities running")) {
        if (System.getProperty("idea.delayed.project.post.startup.activities", "true").toBoolean()) {
          withContext(tracer.span("fully opened editors waiting")) {
            (project.serviceAsync<FileEditorManager>() as? FileEditorManagerEx)?.waitForTextEditors()
          }
        }

        withContext(tracer.span("runPostStartupActivities")) {
          runPostStartupActivities(async = true)
        }
      }
    }
  }

  private suspend fun runInitProjectActivities() {
    runActivities(initProjectStartupActivities)

    val extensionPoint = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint<InitProjectActivity>("com.intellij.initProjectActivity")
    // do not create an extension if not allow-listed
    for (adapter in extensionPoint.sortedAdapters) {
      coroutineContext.ensureActive()

      val pluginId = adapter.pluginDescriptor.pluginId
      if (!isCorePlugin(adapter.pluginDescriptor) && pluginId.idString != "com.jetbrains.performancePlugin"
          && pluginId.idString != "com.jetbrains.performancePlugin.yourkit"
          && pluginId.idString != "com.intellij.clion-swift"
          && pluginId.idString != "com.intellij.clion.performanceTesting"
          && pluginId.idString != "com.intellij.appcode"
          && pluginId.idString != "com.intellij.kmm"
          && pluginId.idString != "com.intellij.clion.plugin"
          && pluginId.idString != "com.jetbrains.codeWithMe"
          && pluginId.idString != "org.jetbrains.plugins.clion.radler") {
        LOG.error("Only bundled plugin can define ${extensionPoint.name}: ${adapter.pluginDescriptor}")
        continue
      }

      val activity = adapter.createInstance<InitProjectActivity>(project) ?: continue
      val startTime = System.nanoTime()
      withContext(tracer.span("run activity", arrayOf("class", activity.javaClass.name, "plugin", pluginId.idString))) {
        if (project !is LightEditCompatible || activity is LightEditCompatible) {
          activity.run(project)
        }
      }

      addCompletedActivity(startTime = startTime, runnableClass = activity.javaClass, pluginId = pluginId)
    }
  }

  // Must be executed in a pooled thread outside a project loading modal task. The only exclusion - test mode.
  private suspend fun runPostStartupActivities(async: Boolean) {
    try {
      LOG.assertTrue(isInitProjectActivitiesPassed)
      val snapshot = PerformanceWatcher.takeSnapshot()
      // strictly speaking, the activity is not sequential, because sub-activities are performed in different threads
      // (depending on dumb-awareness), but because there is no other concurrent phase, we measure it as a sequential activity
      // to put it on the timeline and make clear what's going at the end (avoiding the last "unknown" phase)
      val dumbAwareActivity = StartUpMeasurer.startActivity(StartUpMeasurer.Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES)
      val counter = AtomicInteger()
      val dumbService = project.serviceAsync<DumbService>()
      val isProjectLightEditCompatible = project is LightEditCompatible
      project as ComponentManagerImpl
      for (item in StartupActivity.POST_STARTUP_ACTIVITY.filterableLazySequence()) {
        val activity = item.instance ?: continue
        if (isProjectLightEditCompatible && activity !is LightEditCompatible) {
          continue
        }

        val pluginDescriptor = item.pluginDescriptor

        if (activity is ProjectActivity) {
          if (async) {
            launchActivity(activity = activity, project = project, pluginId = pluginDescriptor.pluginId)
          }
          else {
            activity.execute(project)
          }
          continue
        }

        @Suppress("SSBasedInspection", "UsagesOfObsoleteApi")
        if (activity is DumbAware) {
          if (pluginDescriptor.pluginId == PluginManagerCore.CORE_ID) {
            LOG.warn(PluginException("Migrate ${item.implementationClassName} to ProjectActivity", pluginDescriptor.pluginId))
          }
          dumbService.runWithWaitForSmartModeDisabled().use {
            blockingContext {
              runOldActivity(activity as StartupActivity)
            }
          }
          continue
        }
        else if (!isProjectLightEditCompatible) {
          LOG.warn(PluginException("Migrate ${item.implementationClassName} to ProjectActivity", pluginDescriptor.pluginId))
          // DumbService.unsafeRunWhenSmart throws an assertion in LightEdit mode, see LightEditDumbService.unsafeRunWhenSmart
          counter.incrementAndGet()
          blockingContext {
            dumbService.runWhenSmart {
              runOldActivity(activity as StartupActivity)
            }
          }
        }
      }

      withContext(tracer.span("run post-startup dynamically registered activities")) {
        freezePostStartupActivities = true
        runActivities(postStartupActivities)
      }
      postStartupActivitiesPassed = DUMB_AWARE_PASSED
      postStartupActivitiesPassed = ALL_PASSED
      allActivitiesPassed.complete(value = null)
      dumbAwareActivity.end()

      coroutineContext.ensureActive()

      serviceAsync<StartUpPerformanceService>().projectDumbAwareActivitiesFinished()

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        coroutineContext.ensureActive()

        snapshot.logResponsivenessSinceCreation("Post-startup activities under progress")

        scheduleBackgroundPostStartupActivities(project, coroutineScope)
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

  private fun runOldActivity(@Suppress("UsagesOfObsoleteApi") activity: StartupActivity) {
    try {
      if (project !is LightEditCompatible || activity is LightEditCompatible) {
        activity.runActivity(project)
      }
    }
    catch (e: Throwable) {
      if (e is ControlFlowException || e is CancellationException) {
        throw e
      }
      LOG.error(e)
    }
  }

  private suspend fun runActivities(activities: Deque<Runnable>) {
    synchronized(lock) {
      if (activities.isEmpty()) {
        return
      }
    }

    coroutineScope {
      while (true) {
        coroutineContext.ensureActive()

        val runnable = synchronized(lock, activities::pollFirst) ?: break
        val runnableClass = runnable.javaClass
        val pluginId = (runnableClass.classLoader as? PluginAwareClassLoader)?.pluginId ?: PluginManagerCore.CORE_ID
        launch(tracer.span("run activity", arrayOf("class", runnableClass.name, "plugin", pluginId.idString))) {
          runCatching {
            blockingContext {
              runnable.run()
            }
          }.getOrLogException(LOG)
        }
      }
    }
  }

  override fun runWhenProjectIsInitialized(action: Runnable) {
    if (DumbService.isDumbAware(action)) {
      runAfterOpened { ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), project.disposed, action) }
    }
    else if (!LightEdit.owns(project)) {
      runAfterOpened { DumbService.getInstance(project).runWhenSmart(action) }
    }
  }

  override fun runAfterOpened(runnable: Runnable) {
    checkNonDefaultProject()
    if (!freezePostStartupActivities) {
      synchronized(lock) {
        if (!freezePostStartupActivities) {
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
      initProjectStartupActivities.clear()
      postStartupActivities.clear()
      freezePostStartupActivities = false
    }
  }

  @TestOnly
  @Synchronized
  fun checkCleared() {
    try {
      synchronized(lock) {
        assert(initProjectStartupActivities.isEmpty()) { "Activities: $initProjectStartupActivities" }
        assert(postStartupActivities.isEmpty()) { "DumbAware Post Activities: $postStartupActivities" }
      }
    }
    finally {
      prepareForNextTest()
    }
  }
}

private fun scheduleBackgroundPostStartupActivities(project: Project, coroutineScope: CoroutineScope) {
  coroutineScope.launch {
    delay(Registry.intValue("ide.background.post.startup.activity.delay", 5_000).toLong())
    // read action - dynamic plugin loading executed as a write action
    // readActionBlocking because maybe called several times, but addExtensionPointListener must be added only once
    val activities = readActionBlocking {
      BACKGROUND_POST_STARTUP_ACTIVITY.addExtensionPointListener(object : ExtensionPointListener<Any> {
        override fun extensionAdded(extension: Any, pluginDescriptor: PluginDescriptor) {
          launchBackgroundPostStartupActivity(activity = extension,
                                              pluginId = pluginDescriptor.pluginId,
                                              project = project)
        }
      }, project)
      BACKGROUND_POST_STARTUP_ACTIVITY.filterableLazySequence()
    }

    if (!isActive) {
      return@launch
    }

    for (extension in activities) {
      launchBackgroundPostStartupActivity(activity = extension.instance ?: continue,
                                          pluginId = extension.pluginDescriptor.pluginId,
                                          project = project)
    }
  }
}

private fun launchBackgroundPostStartupActivity(activity: Any, pluginId: PluginId, project: Project) {
  if (project is LightEditCompatible && activity !is LightEditCompatible) {
    return
  }

  if (activity is ProjectActivity) {
    launchActivity(activity, project, pluginId)
    return
  }

  ((project as ComponentManagerImpl).instanceCoroutineScope(activity.javaClass)).launch {
    try {
      blockingContext {
        @Suppress("UsagesOfObsoleteApi")
        (activity as StartupActivity).runActivity(project)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      if (e is ControlFlowException) {
        throw e
      }
      LOG.error(e)
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

private fun launchActivity(activity: ProjectActivity, project: Project, pluginId: PluginId) {
  (project as ComponentManagerImpl).pluginCoroutineScope(activity.javaClass.classLoader).launch(
    tracer.rootSpan(name = "run activity", arrayOf("class", activity.javaClass.name, "plugin", pluginId.idString))
  ) {
    activity.execute(project)
  }
}

// allow `invokeAndWait` inside startup activities
private suspend fun waitAndProcessInvocationEventsInIdeEventQueue(startupManager: StartupManagerImpl) {
  ThreadingAssertions.assertEventDispatchThread()
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