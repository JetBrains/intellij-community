// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
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
import com.intellij.openapi.observable.util.setObservableProperty
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
import kotlin.collections.component1
import kotlin.collections.component2
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

  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    /* name = */ "AutoImportProjectTracker.backgroundExecutor",
    /* maxThreads = */ 1
  )

  private val dispatcher = MergingUpdateQueue(
    name = "AutoImportProjectTracker.dispatcher",
    mergingTimeSpan = MERGING_TIME_SPAN_MS.toInt(),
    isActive = true,
    modalityStateComponent = null,
    parent = serviceDisposable,
    executeInDispatchThread = false
  )

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
    LOG.debug("Schedule change processing (isExplicitReload=true)", Throwable())

    schedule(priority = 0, dispatchIterations = 1) {
      processChanges(isExplicitReload = true)
    }
  }

  override fun scheduleChangeProcessing() {
    LOG.debug("Schedule change processing (isExplicitReload=false)")

    schedule(priority = 1, dispatchIterations = 1) {
      processChanges(isExplicitReload = false)
    }
  }

  private fun scheduleDelayedProjectReload(projectsToReload: List<Pair<ExternalSystemProjectAware, ExternalSystemProjectReloadContext>>) {
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

    schedule(priority = 2, dispatchIterations = dispatchIterations) {
      reloadProject(projectsToReload)
    }
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

  private fun processChanges(isExplicitReload: Boolean) {
    LOG.debug("Process changes (isExplicitReload=$isExplicitReload)")

    val projectsToReload = ArrayList<Pair<ExternalSystemProjectAware, ExternalSystemProjectReloadContext>>()
    for (projectData in projectDataMap.values) {
      val context = ProjectReloadContext(
        isExplicitReload = isExplicitReload,
        hasUndefinedModifications = !projectData.status.isUpToDate(),
        settingsFilesContext = projectData.settingsTracker.getSettingsContext()
      )
      val projectId = projectData.projectAware.projectId
      when {
        projectData.isUpToDate() -> {
          LOG.debug("$projectId: Skip project reload (UpToDate)")
          notificationAware.notificationExpire(projectId)
        }
        isDisabledReload(projectData, context) -> {
          LOG.debug("$projectId: Skip project reload (disabled)")
          notificationAware.notificationExpire(projectId)
        }
        !isExplicitReload && isDisabledAutoReload(projectData, context) -> {
          LOG.debug("$projectId: Skip project auto-reload (disabled)")
          notificationAware.notificationNotify(projectData.projectAware)
        }
        else -> {
          LOG.debug("$projectId: Schedule project reload")
          notificationAware.notificationExpire(projectId)
          projectsToReload.add(projectData.projectAware to context)
        }
      }
    }

    if (projectsToReload.isEmpty()) {
      LOG.debug("Skip all project reloads")
    }
    else if (isExplicitReload) {
      reloadProject(projectsToReload)
    }
    else {
      scheduleDelayedProjectReload(projectsToReload)
    }
  }

  private fun reloadProject(projectsToReload: List<Pair<ExternalSystemProjectAware, ExternalSystemProjectReloadContext>>) {
    LOG.debug("Reload projects")

    invokeAndWaitIfNeeded { // needed for backward compatibility
      for ((projectAware, context) in projectsToReload) {
        LOG.debug("${projectAware.projectId}: reload project")
        projectAware.reloadProject(context)
      }
    }
  }

  private fun isDisabledReload(projectData: ProjectData, context: ExternalSystemProjectReloadContext): Boolean {
    val projectId = projectData.projectAware.projectId

    if (!isEnabledReload) {
      LOG.debug("$projectId: Disabled reload (global property)")
      return true
    }

    if (projectChangeOperation.isOperationInProgress()) {
      LOG.debug("$projectId: Disabled reload (project change)")
      return true
    }

    if (projectReloadOperation.isOperationInProgress()) {
      LOG.debug("$projectId: Disabled reload (project reload)")
      return true
    }

    if (projectData.projectAware.isDisabledReload(context)) {
      LOG.debug("$projectId: Disabled reload (custom)")
      return true
    }

    return false
  }

  private fun isDisabledAutoReload(projectData: ProjectData, context: ExternalSystemProjectReloadContext): Boolean {
    val projectId = projectData.projectAware.projectId

    if (!isEnableAutoReload) {
      LOG.debug("$projectId: Disabled auto-reload (global property)")
      return true
    }

    if (!projectData.isActivated) {
      LOG.debug("$projectId: Disabled auto-reload (activation)")
      return true
    }

    if (isProjectLookupActivateProperty.get()) {
      LOG.debug("$projectId: Disabled auto-reload (project lookup)")
      return true
    }

    val autoReloadType = settings.autoReloadType
    val modificationType = projectData.getModificationType()
    val isDisabledAutoReload = when (autoReloadType) {
      AutoReloadType.ALL -> when (modificationType) {
        EXTERNAL -> false
        INTERNAL -> false
        HIDDEN -> true
        UNKNOWN -> true
      }
      AutoReloadType.SELECTIVE -> when (modificationType) {
        EXTERNAL -> false
        INTERNAL -> true
        HIDDEN -> true
        UNKNOWN -> true
      }
      AutoReloadType.NONE -> true
    }
    if (isDisabledAutoReload) {
      LOG.debug("$projectId: Disabled auto-reload ($autoReloadType, $modificationType)")
      return true
    }

    if (projectData.projectAware.isDisabledAutoReload(context)) {
      LOG.debug("$projectId: Disabled auto-reload (custom)")
      return true
    }

    return false
  }

  override fun register(projectAware: ExternalSystemProjectAware) {
    val projectId = projectAware.projectId
    val activationProperty = AtomicBooleanProperty(false)
    val projectStatus = ProjectStatus(debugName = projectId.toString())
    val parentDisposable = Disposer.newDisposable(serviceDisposable, projectId.toString())
    val settingsTracker = ProjectSettingsTracker(project, this, backgroundExecutor, projectAware, parentDisposable)
    val projectData = ProjectData(projectStatus, activationProperty, projectAware, settingsTracker, parentDisposable)

    projectDataMap[projectId] = projectData

    settingsTracker.beforeApplyChanges(parentDisposable) { projectReloadOperation.traceStart() }
    settingsTracker.afterApplyChanges(parentDisposable) { projectReloadOperation.traceFinish() }
    activationProperty.whenPropertySet(parentDisposable) { LOG.debug("$projectId is activated") }
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
      scheduleChangeProcessing()
    }
    projectReloadOperation.whenOperationFinished(serviceDisposable) {
      LOG.debug("Detected project reload finish event")
      scheduleChangeProcessing()
    }
    projectChangeOperation.whenOperationStarted(serviceDisposable) {
      LOG.debug("Detected project change start event")
      scheduleChangeProcessing()
    }
    projectChangeOperation.whenOperationFinished(serviceDisposable) {
      LOG.debug("Detected project change finish event")
      scheduleChangeProcessing()
    }
    isProjectLookupActivateProperty.whenPropertySet(serviceDisposable) {
      LOG.debug("Detected project lookup start event")
      scheduleChangeProcessing()
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

    private val isEnabledReload: Boolean
      get() = onceIgnoreDisableAutoReloadRegistryProperty.getAndSet(false) ||
              !Registry.`is`("external.system.auto.import.disabled")

    private val isEnableAutoReload: Boolean by enableAutoReloadProperty

    val isAsyncChangesProcessing: Boolean by asyncChangesProcessingProperty

    /**
     * Enables auto-import in tests
     * Note: project tracker automatically enabled out of tests
     */
    @TestOnly
    fun enableAutoReloadInTests(parentDisposable: Disposable) {
      setObservableProperty(enableAutoReloadProperty, true, parentDisposable)
    }

    /**
     * Enables async auto-reload processing in tests
     * Note: async processing enabled out of tests
     */
    @TestOnly
    fun enableAsyncAutoReloadInTests(parentDisposable: Disposable) {
      setObservableProperty(asyncChangesProcessingProperty, true, parentDisposable)
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