// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros.CACHE_FILE
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Util.bindIsPassThrough
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Util.bindMergingTimeSpan
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.HIDDEN
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.UNKNOWN
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.update.PriorityEatUpdate
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.MutableOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.MutableBooleanProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.openapi.observable.properties.whenPropertyReset
import com.intellij.openapi.observable.properties.whenPropertySet
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.setObservableProperty
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.queueTracked
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Internal
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
  private val isProjectLookupActivateProperty = AtomicBooleanProperty(false)

  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    /* name = */ "AutoImportProjectTracker.backgroundExecutor",
    /* maxThreads = */ 1
  )

  private val dispatcher = MergingUpdateQueue(
    name = "AutoImportProjectTracker.dispatcher",
    mergingTimeSpan = mergingTimeSpan.inWholeMilliseconds.toInt(),
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
        projectData.reloadOperation.traceStart()
        projectData.status.markSynchronized(Stamp.nextStamp())
        projectData.isActivated = true
      }

      override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
        if (status != SUCCESS) {
          projectData.status.markBroken(Stamp.nextStamp())
        }
        projectData.reloadOperation.traceFinish()
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

  /**
   * @see AutoImportProjectTracker.scheduleProjectRefresh
   * @see AutoImportProjectTracker.scheduleChangeProcessing
   */
  private fun scheduleDelayedProjectReload(projectsToReload: List<Pair<ExternalSystemProjectAware, ExternalSystemProjectReloadContext>>) {
    LOG.debug("Schedule delayed project reload")

    val smartProjectReloadDelay = projectDataMap.values.asSequence()
      .mapNotNull { it.projectAware.smartProjectReloadDelay }
      .maxOrNull() ?: autoReloadDelay
    val dispatcherIterations = smartProjectReloadDelay / mergingTimeSpan
    // We already dispatched `processChanges` with the `mergingTimeSpan` delay
    // Therefore we need to reduce effective
    val effectiveDispatchIterations = maxOf(dispatcherIterations.toInt() - 1, 1)

    schedule(priority = 2, dispatchIterations = effectiveDispatchIterations) {
      reloadProject(projectsToReload)
    }
  }

  private val currentActivityLock = ReentrantLock()
  private var currentActivity: ProjectInitializationDiagnosticService.ActivityTracker? = null

  private fun schedule(priority: Int, dispatchIterations: Int, action: () -> Unit) {
    currentActivityLock.withLock { 
      if (currentActivity == null) {
        currentActivity = ProjectInitializationDiagnosticService.registerTracker(project, "AutoImportProjectTracker.schedule")
      }
    }
    dispatcher.queueTracked(PriorityEatUpdate(priority) {
      project.trackActivityBlocking(ExternalSystemActivityKey) {
        if (dispatchIterations - 1 > 0) {
          schedule(priority, dispatchIterations - 1, action)
        }
        else {
          action()
          if (dispatcher.isEmpty) {
            currentActivityLock.withLock {
              currentActivity?.activityFinished()
              currentActivity = null
            }
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

    if (!projectData.isActivated) {
      LOG.debug("$projectId: Disabled reload (activation)")
      return true
    }

    if (projectChangeOperation.isOperationInProgress()) {
      LOG.debug("$projectId: Disabled reload (project change)")
      return true
    }

    if (projectData.reloadOperation.isOperationInProgress()) {
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

    if (!isEnabledAutoReload) {
      LOG.debug("$projectId: Disabled auto-reload (global property)")
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
    val reloadOperation = AtomicOperationTrace(name = "Reload $projectId")
    val projectStatus = AutoImportProjectStatus(debugName = projectId.toString())
    val parentDisposable = Disposer.newDisposable(serviceDisposable, projectId.toString())
    val settingsTracker = AutoImportProjectSettingsFilesTracker(project, this, backgroundExecutor, projectAware, parentDisposable)
    val projectData = ProjectData(projectStatus, activationProperty, reloadOperation, projectAware, settingsTracker, parentDisposable)

    projectDataMap[projectId] = projectData

    settingsTracker.beforeApplyChanges(parentDisposable) { reloadOperation.traceStart() }
    settingsTracker.afterApplyChanges(parentDisposable) { reloadOperation.traceFinish() }

    activationProperty.whenPropertySet(parentDisposable) {
      LOG.debug("$projectId is activated")
      scheduleChangeProcessing()
    }
    reloadOperation.whenOperationStarted(serviceDisposable) {
      LOG.debug("$projectId: Detected project reload start event")
      scheduleChangeProcessing()
    }
    reloadOperation.whenOperationFinished(serviceDisposable) {
      LOG.debug("$projectId: Detected project reload finish event")
      scheduleChangeProcessing()
    }

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
    if (settingsTrackerState == null || projectState.isDirty || isWorkspaceModelCacheAbsentOrInvalid()) {
      projectData.status.markDirty(Stamp.nextStamp(), EXTERNAL)
      scheduleChangeProcessing()
      return
    }
    projectData.settingsTracker.loadState(settingsTrackerState)
    projectData.settingsTracker.refreshChanges()
  }

  private fun isWorkspaceModelCacheAbsentOrInvalid(): Boolean {
    // Unknown WorkspaceModel implementation (e.g. in Rider) -> unknown state
    val workspaceModel = project.workspaceModel as? WorkspaceModelImpl ?: return false
    // Cache disabled (e.g. in tests) -> unknown state
    if (!project.service<WorkspaceModelCache>().enabled) return false
    // WorkspaceModel isn't loaded from cache if it was never saved or invalidated
    return !workspaceModel.loadedFromCache
  }

  @TestOnly
  fun getActivatedProjects(): Set<ExternalSystemProjectId> {
    return projectDataMap.values
      .asSequence()
      .filter { it.isActivated }
      .map { it.projectAware.projectId }
      .toSet()
  }

  init {
    LOG.debug("Project tracker initialization")

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

    dispatcher.bindIsPassThrough(asyncChangesProcessingProperty.not(), serviceDisposable)
    dispatcher.bindMergingTimeSpan(mergingTimeSpanProperty, serviceDisposable)

    dispatcher.setRestartTimerOnAdd(true)

    application.messageBus.connect(serviceDisposable)
      .subscribe(BatchFileChangeListener.TOPIC, createProjectChangesListener())
    project.messageBus.connect(serviceDisposable)
      .subscribe(LookupManagerListener.TOPIC, createProjectCompletionListener())
  }

  private data class ProjectData(
    @JvmField val status: AutoImportProjectStatus,
    @JvmField val activationProperty: MutableBooleanProperty,
    @JvmField val reloadOperation: MutableOperationTrace,
    @JvmField val projectAware: ExternalSystemProjectAware,
    @JvmField val settingsTracker: AutoImportProjectSettingsFilesTracker,
    @JvmField val parentDisposable: Disposable
  ) {
    var isActivated by activationProperty

    fun isUpToDate() = status.isUpToDate() && settingsTracker.isUpToDate()

    fun getModificationType(): ExternalSystemModificationType {
      return AutoImportProjectStatus.merge(status.getModificationType(), settingsTracker.getModificationType())
    }
  }

  @Serializable
  data class State(
    @JvmField val projectData: Map<String, Map<String, ProjectDataState>> = emptyMap()
  )

  @Serializable
  data class ProjectDataState(
    @JvmField val isDirty: Boolean = false,
    @JvmField val settingsTracker: AutoImportProjectSettingsFilesTracker.State? = null
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

    private val onceIgnoreDisableReloadRegistryProperty = AtomicBooleanProperty(false)

    private val enableReloadProperty = AtomicBooleanProperty(!application.isUnitTestMode)

    private val enableAutoReloadProperty = AtomicBooleanProperty(!application.isUnitTestMode)

    private val asyncChangesProcessingProperty = AtomicBooleanProperty(
      !application.isHeadlessEnvironment
      || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()
      || java.lang.Boolean.getBoolean("external.system.auto.import.headless.async")
    )

    private val mergingTimeSpanProperty = AtomicProperty(300.milliseconds)

    private val autoReloadDelayProperty = AtomicProperty(3.seconds)

    private val isEnabledReload: Boolean
      get() = enableReloadProperty.get() &&
              (onceIgnoreDisableReloadRegistryProperty.getAndSet(false) ||
               !Registry.`is`("external.system.auto.import.disabled"))

    private val isEnabledAutoReload: Boolean
      get() = isEnabledReload && enableAutoReloadProperty.get()

    val isAsyncChangesProcessing: Boolean by asyncChangesProcessingProperty


    private var mergingTimeSpan: Duration by mergingTimeSpanProperty

    private var autoReloadDelay: Duration by autoReloadDelayProperty

    /**
     * In tests, enables only manual syncs executed using the [ExternalSystemProjectTracker] API.
     *
     * Note: auto-sync still will be disabled.
     */
    @TestOnly
    fun enableReloadInTests(parentDisposable: Disposable) {
      setObservableProperty(enableReloadProperty, true, parentDisposable)
    }

    /**
     * In tests, enables all syncs executed using the [ExternalSystemProjectTracker] API.
     *
     * Note: manual syncs will be also enabled.
     */
    @TestOnly
    fun enableAutoReloadInTests(parentDisposable: Disposable) {
      setObservableProperty(enableReloadProperty, true, parentDisposable)
      setObservableProperty(enableAutoReloadProperty, true, parentDisposable)
    }

    /**
     * In tests, enables async project status processing.
     */
    @TestOnly
    fun enableAsyncAutoReloadInTests(parentDisposable: Disposable) {
      setObservableProperty(asyncChangesProcessingProperty, true, parentDisposable)
    }

    /**
     * Ignores once disable auto-reload registry.
     * Make sense only in a pair with registry key `external.system.auto.import.disabled`.
     */
    @Internal
    fun onceIgnoreDisableAutoReloadRegistry() {
      onceIgnoreDisableReloadRegistryProperty.set(true)
    }

    /**
     * Defines the delay minimum project tracker delay.
     *
     * All project changes and sync requests that were made during this delay rescheduled it.
     * So, it allows us to merge all project changes and sync requests into one bulk sync request.
     * So, it allows us to start syncing automatically only once.
     */
    @TestOnly
    fun setMergingTimeSpan(delay: Duration, parentDisposable: Disposable) {
      setObservableProperty(mergingTimeSpanProperty, delay, parentDisposable)
    }

    /**
     * Defines the delay from the detected project change to the start of auto-sync.
     *
     * @see setMergingTimeSpan
     */
    @TestOnly
    fun setAutoReloadDelay(delay: Duration, parentDisposable: Disposable) {
      setObservableProperty(autoReloadDelayProperty, delay, parentDisposable)
    }
  }

  private object Util {

    fun <Q : MergingUpdateQueue> Q.bindIsPassThrough(
      property: ObservableProperty<Boolean>,
      parentDisposable: Disposable? = null,
    ): Q = apply {
      isPassThrough = property.get()
      property.whenPropertyChanged(parentDisposable) {
        isPassThrough = it
      }
    }

    fun <Q : MergingUpdateQueue> Q.bindMergingTimeSpan(
      property: ObservableProperty<Duration>,
      parentDisposable: Disposable? = null,
    ): Q = apply {
      setMergingTimeSpan(property.get())
      property.whenPropertyChanged(parentDisposable) {
        setMergingTimeSpan(property.get())
      }
    }

    fun <Q : MergingUpdateQueue> Q.setMergingTimeSpan(span: Duration): Q =
      apply { setMergingTimeSpan(span.inWholeMilliseconds.toInt()) }
  }
}
