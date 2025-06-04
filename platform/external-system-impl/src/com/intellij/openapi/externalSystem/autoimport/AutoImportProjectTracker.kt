// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.CACHE_FILE
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.update.PriorityEatUpdate
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.observable.properties.*
import com.intellij.openapi.observable.util.set
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.queueTracked
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.streams.asStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val MERGING_TIME_SPAN = 300.milliseconds
private val MERGING_TIME_SPAN_MS = MERGING_TIME_SPAN.inWholeMilliseconds

private val DEFAULT_SMART_PROJECT_RELOAD_DELAY = 3.seconds

@ApiStatus.Internal
@State(name = "ExternalSystemProjectTracker", storages = [Storage(CACHE_FILE)])
class AutoImportProjectTracker(
  private val project: Project
) : ExternalSystemProjectTracker,
    Disposable.Default,
    PersistentStateComponent<AutoImportProjectTracker.State> {

  private val serviceDisposable: Disposable = this

  private val settings = AutoImportProjectTrackerSettings.getInstance(project)
  private val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)

  private val projectDataStates = ConcurrentHashMap<ExternalSystemProjectId, ProjectDataState>()
  private val projectDataMap = ConcurrentHashMap<ExternalSystemProjectId, ProjectData>()
  private val projectChangeOperation = AtomicOperationTrace(name = "Project change operation")
  private val projectReloadOperation = AtomicOperationTrace(name = "Project reload operation")
  private val isProjectLookupActivateProperty = AtomicBooleanProperty(false)
  private val dispatcher = MergingUpdateQueue("AutoImportProjectTracker.dispatcher", MERGING_TIME_SPAN_MS.toInt(), true, null, serviceDisposable)
  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AutoImportProjectTracker.backgroundExecutor", 1)

  private fun createProjectChangesListener() =
    object : ProjectBatchFileChangeListener(project) {
      override fun batchChangeStarted(activityName: String?) =
        projectChangeOperation.traceStart()

      override fun batchChangeCompleted() =
        projectChangeOperation.traceFinish()
    }

  private fun createProjectReloadListener(projectData: ProjectData) =
    object : ExternalSystemProjectListener {

      override fun onProjectReloadStart() {
        projectReloadOperation.traceStart()
        projectData.status.markSynchronized(Stamp.nextStamp())
        projectData.isActivated = true
      }

      override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
        if (status != SUCCESS) {
          projectData.status.markBroken(Stamp.nextStamp())
        }
        projectReloadOperation.traceFinish()
      }
    }

  private fun createProjectCompletionListener() = LookupManagerListener { _, newLookup ->
    isProjectLookupActivateProperty.set(newLookup != null)
  }

  override fun scheduleProjectRefresh() {
    LOG.debug("Schedule project reload", Throwable())
    schedule(priority = 0, dispatchIterations = 1) {
      reloadProject(explicitReload = true)
    }
  }

  override fun scheduleChangeProcessing() {
    LOG.debug("Schedule change processing")
    schedule(priority = 1, dispatchIterations = 1) { processChanges() }
  }

  private fun scheduleDelayedSmartProjectReload() {
    LOG.debug("Schedule delayed project reload")

    // See AutoImportProjectTracker.scheduleChangeProcessing for details
    val smartProjectReloadDelay = projectDataMap.values.maxOfOrNull {
      it.projectAware.smartProjectReloadDelay ?: DEFAULT_SMART_PROJECT_RELOAD_DELAY
    } ?: DEFAULT_SMART_PROJECT_RELOAD_DELAY
    // We already dispatched processChanges with the MERGING_TIME_SPAN delay
    // See AutoImportProjectTracker.scheduleChangeProcessing for details
    val smartProjectReloadDispatcherIterations = ((smartProjectReloadDelay - MERGING_TIME_SPAN) / MERGING_TIME_SPAN).toInt()
    // smartProjectReloadDispatcherIterations can be negative if smartProjectReloadDelay is less than MERGING_TIME_SPAN
    val dispatchIterations = maxOf(smartProjectReloadDispatcherIterations, 1)

    schedule(priority = 2, dispatchIterations = dispatchIterations) { reloadProject(explicitReload = false) }
  }

  private val currentActivity = AtomicReference<ProjectInitializationDiagnosticService.ActivityTracker?>()

  private fun schedule(priority: Int, dispatchIterations: Int, action: () -> Unit) {
    currentActivity.updateAndGet {
      it ?: ProjectInitializationDiagnosticService.registerTracker(project, "AutoImportProjectTracker.schedule")
    }
    dispatcher.queueTracked(PriorityEatUpdate(priority) {
      project.trackActivityBlocking(ExternalSystemActivityKey) {
        if (dispatchIterations - 1 > 0) {
          schedule(priority, dispatchIterations - 1, action)
        }
        else {
          action()
          if (dispatcher.isEmpty) {
            currentActivity.getAndSet(null)?.activityFinished()
          }
        }
      }
    })
  }

  private fun processChanges() {
    LOG.debug("Process changes")

    when (settings.autoReloadType) {
      AutoReloadType.ALL -> when (getModificationType()) {
        INTERNAL -> scheduleDelayedSmartProjectReload()
        EXTERNAL -> scheduleDelayedSmartProjectReload()
        HIDDEN -> updateProjectNotification()
        UNKNOWN -> updateProjectNotification()
      }
      AutoReloadType.SELECTIVE -> when (getModificationType()) {
        INTERNAL -> updateProjectNotification()
        EXTERNAL -> scheduleDelayedSmartProjectReload()
        HIDDEN -> updateProjectNotification()
        UNKNOWN -> updateProjectNotification()
      }
      AutoReloadType.NONE -> updateProjectNotification()
    }
  }

  private fun reloadProject(explicitReload: Boolean) {
    LOG.debug("Incremental project reload")

    val projectsToReload = projectDataMap.values
      .filter { (explicitReload || it.isActivated) && !it.isUpToDate() }

    if (isDisabledAutoReload() || projectsToReload.isEmpty()) {
      LOG.debug("Skipped all projects reload")
      updateProjectNotification()
      return
    }

    for (projectData in projectsToReload) {
      LOG.debug("${projectData.projectAware.projectId.debugName}: Project reload")
      val hasUndefinedModifications = !projectData.status.isUpToDate()
      val settingsContext = projectData.settingsTracker.getSettingsContext()
      val context = ProjectReloadContext(explicitReload, hasUndefinedModifications, settingsContext)
      projectData.projectAware.reloadProject(context)
    }
  }

  private fun updateProjectNotification() {
    LOG.debug("Notification status update")

    val isDisabledAutoReload = isDisabledAutoReload()
    for ((projectId, data) in projectDataMap) {
      when (isDisabledAutoReload || data.isUpToDate()) {
        true -> notificationAware.notificationExpire(projectId)
        else -> notificationAware.notificationNotify(data.projectAware)
      }
    }
  }

  private fun isDisabledAutoReload(): Boolean {
    return !isEnabledAutoReload ||
           projectChangeOperation.isOperationInProgress() ||
           projectReloadOperation.isOperationInProgress() ||
           isProjectLookupActivateProperty.get()
  }

  private fun getModificationType(): ExternalSystemModificationType {
    return projectDataMap.values
      .asSequence()
      .map { it.getModificationType() }
      .asStream()
      .reduce(ProjectStatus::merge)
      .orElse(UNKNOWN)
  }

  override fun register(projectAware: ExternalSystemProjectAware) {
    val projectId = projectAware.projectId
    val projectIdName = projectId.debugName
    val activationProperty = AtomicBooleanProperty(false)
    val projectStatus = ProjectStatus(debugName = projectIdName)
    val parentDisposable = Disposer.newDisposable(serviceDisposable, projectIdName)
    val settingsTracker = ProjectSettingsTracker(project, this, backgroundExecutor, projectAware, parentDisposable)
    val projectData = ProjectData(projectStatus, activationProperty, projectAware, settingsTracker, parentDisposable)

    projectDataMap[projectId] = projectData

    settingsTracker.beforeApplyChanges(parentDisposable) { projectReloadOperation.traceStart() }
    settingsTracker.afterApplyChanges(parentDisposable) { projectReloadOperation.traceFinish() }
    activationProperty.whenPropertySet(parentDisposable) { LOG.debug("$projectIdName is activated") }
    activationProperty.whenPropertySet(parentDisposable) { scheduleChangeProcessing() }

    projectAware.subscribe(createProjectReloadListener(projectData), parentDisposable)
    parentDisposable.whenDisposed { notificationAware.notificationExpire(projectId) }

    loadState(projectId, projectData)
  }

  override fun activate(id: ExternalSystemProjectId) {
    val projectData = projectDataMap(id) { get(it) } ?: return
    projectData.isActivated = true
  }

  override fun remove(id: ExternalSystemProjectId) {
    val projectData = projectDataMap.remove(id) ?: return
    Disposer.dispose(projectData.parentDisposable)
  }

  override fun markDirty(id: ExternalSystemProjectId) {
    val projectData = projectDataMap(id) { get(it) } ?: return
    projectData.status.markDirty(Stamp.nextStamp())
  }

  override fun markDirtyAllProjects() {
    val modificationTimeStamp = Stamp.nextStamp()
    for (projectData in projectDataMap.values) {
      projectData.status.markDirty(modificationTimeStamp)
    }
  }

  private fun projectDataMap(
    id: ExternalSystemProjectId,
    action: MutableMap<ExternalSystemProjectId, ProjectData>.(ExternalSystemProjectId) -> ProjectData?
  ): ProjectData? {
    val projectData = projectDataMap.action(id)
    if (projectData == null) {
      LOG.warn(String.format("Project isn't registered by id=%s", id), Throwable())
    }
    return projectData
  }

  override fun getState(): State {
    val systemStates = TreeMap<String, TreeMap<String, ProjectDataState>>()
    for ((projectId, projectData) in projectDataMap) {
      val (systemId, externalProjectPath) = projectId
      val projectStates = systemStates.computeIfAbsent(systemId.id) { TreeMap() }
      projectStates[externalProjectPath] = ProjectDataState(
        projectData.status.isDirty(),
        projectData.settingsTracker.getState()
      )
    }
    return State(systemStates)
  }

  override fun loadState(state: State) {
    for ((systemId, systemData) in state.projectData) {
      for ((externalProjectPath, projectData) in systemData) {
        val projectId = ExternalSystemProjectId(ProjectSystemId(systemId), externalProjectPath)
        projectDataStates[projectId] = projectData
      }
    }
    projectDataMap.forEach { (id, data) -> loadState(id, data) }
  }

  private fun loadState(projectId: ExternalSystemProjectId, projectData: ProjectData) {
    val projectState = projectDataStates.remove(projectId)
    val settingsTrackerState = projectState?.settingsTracker
    if (settingsTrackerState == null || projectState.isDirty) {
      projectData.status.markDirty(Stamp.nextStamp(), EXTERNAL)
      scheduleChangeProcessing()
      return
    }
    projectData.settingsTracker.loadState(settingsTrackerState)
    projectData.settingsTracker.refreshChanges()
  }

  @TestOnly
  fun getActivatedProjects(): Set<ExternalSystemProjectId> {
    return projectDataMap.values
      .asSequence()
      .filter { it.isActivated }
      .map { it.projectAware.projectId }
      .toSet()
  }

  @TestOnly
  fun setDispatcherMergingSpan(delay: Int) {
    dispatcher.setMergingTimeSpan(delay)
  }

  init {
    LOG.debug("Project tracker initialization")

    projectReloadOperation.whenOperationStarted(serviceDisposable) {
      LOG.debug("Detected project reload start event")
      notificationAware.notificationExpire()
    }
    projectReloadOperation.whenOperationFinished(serviceDisposable) {
      LOG.debug("Detected project reload finish event")
      scheduleChangeProcessing()
    }
    projectChangeOperation.whenOperationStarted(serviceDisposable) {
      LOG.debug("Detected project change start event")
      notificationAware.notificationExpire()
    }
    projectChangeOperation.whenOperationFinished(serviceDisposable) {
      LOG.debug("Detected project change finish event")
      scheduleChangeProcessing()
    }
    isProjectLookupActivateProperty.whenPropertySet(serviceDisposable) {
      LOG.debug("Detected project lookup start event")
    }
    isProjectLookupActivateProperty.whenPropertyReset(serviceDisposable) {
      LOG.debug("Detected project lookup finish event")
      scheduleChangeProcessing()
    }
    settings.autoReloadTypeProperty.whenPropertyChanged(serviceDisposable) {
      LOG.debug("Detected project reload type change event")
      scheduleChangeProcessing()
    }

    dispatcher.isPassThrough = !asyncChangesProcessingProperty.get()
    asyncChangesProcessingProperty.whenPropertyChanged(serviceDisposable) { dispatcher.isPassThrough = !it }

    dispatcher.setRestartTimerOnAdd(true)

    application.messageBus.connect(serviceDisposable)
      .subscribe(BatchFileChangeListener.TOPIC, createProjectChangesListener())
    project.messageBus.connect(serviceDisposable)
      .subscribe(LookupManagerListener.TOPIC, createProjectCompletionListener())
  }

  private data class ProjectData(
    @JvmField val status: ProjectStatus,
    @JvmField val activationProperty: MutableBooleanProperty,
    @JvmField val projectAware: ExternalSystemProjectAware,
    @JvmField val settingsTracker: ProjectSettingsTracker,
    @JvmField val parentDisposable: Disposable
  ) {
    var isActivated by activationProperty

    fun isUpToDate() = status.isUpToDate() && settingsTracker.isUpToDate()

    fun getModificationType(): ExternalSystemModificationType {
      return ProjectStatus.merge(status.getModificationType(), settingsTracker.getModificationType())
    }
  }

  @Serializable
  data class State(
    @JvmField val projectData: Map<String, Map<String, ProjectDataState>> = emptyMap()
  )

  @Serializable
  data class ProjectDataState(
    @JvmField val isDirty: Boolean = false,
    @JvmField val settingsTracker: ProjectSettingsTracker.State? = null
  )

  private data class ProjectReloadContext(
    override val isExplicitReload: Boolean,
    override val hasUndefinedModifications: Boolean,
    override val settingsFilesContext: ExternalSystemSettingsFilesReloadContext
  ) : ExternalSystemProjectReloadContext

  companion object {

    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")

    fun getInstance(project: Project): AutoImportProjectTracker {
      return ExternalSystemProjectTracker.getInstance(project) as AutoImportProjectTracker
    }

    private val onceIgnoreDisableAutoReloadRegistryProperty = AtomicBooleanProperty(false)

    private val enableAutoReloadProperty = AtomicBooleanProperty(
      !ApplicationManager.getApplication().isUnitTestMode
    )

    private val asyncChangesProcessingProperty = AtomicBooleanProperty(
      !ApplicationManager.getApplication().isHeadlessEnvironment
      || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()
      || java.lang.Boolean.getBoolean("external.system.auto.import.headless.async")
    )

    private val isEnabledAutoReload: Boolean
      get() = enableAutoReloadProperty.get() &&
              (onceIgnoreDisableAutoReloadRegistryProperty.getAndSet(false) ||
               !Registry.`is`("external.system.auto.import.disabled"))

    val isAsyncChangesProcessing: Boolean
      get() = asyncChangesProcessingProperty.get()

    /**
     * Enables auto-import in tests
     * Note: project tracker automatically enabled out of tests
     */
    @TestOnly
    fun enableAutoReloadInTests(parentDisposable: Disposable) {
      enableAutoReloadProperty.set(true, parentDisposable)
    }

    /**
     * Enables async auto-reload processing in tests
     * Note: async processing enabled out of tests
     */
    @TestOnly
    fun enableAsyncAutoReloadInTests(parentDisposable: Disposable) {
      asyncChangesProcessingProperty.set(true, parentDisposable)
    }

    /**
     * Ignores once disable auto-reload registry.
     * Make sense only in a pair with registry key `external.system.auto.import.disabled`.
     */
    @ApiStatus.Internal
    fun onceIgnoreDisableAutoReloadRegistry() {
      onceIgnoreDisableAutoReloadRegistryProperty.set(true)
    }
  }
}