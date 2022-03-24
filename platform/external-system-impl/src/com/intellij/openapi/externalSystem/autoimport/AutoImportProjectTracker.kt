// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.ide.file.BatchFileChangeListener
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
import com.intellij.openapi.externalSystem.autoimport.update.PriorityEatUpdate
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.BooleanProperty
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.LocalTimeCounter.currentTime
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.asStream

@State(name = "ExternalSystemProjectTracker", storages = [Storage(CACHE_FILE)])
class AutoImportProjectTracker(private val project: Project) : ExternalSystemProjectTracker, PersistentStateComponent<AutoImportProjectTracker.State> {
  private val settings get() = AutoImportProjectTrackerSettings.getInstance(project)
  private val projectStates = ConcurrentHashMap<State.Id, State.Project>()
  private val projectDataMap = ConcurrentHashMap<ExternalSystemProjectId, ProjectData>()
  private val isDisabled = AtomicBooleanProperty(isDisabledAutoReload.get())
  private val asyncChangesProcessingProperty = AtomicBooleanProperty(
    !ApplicationManager.getApplication().isHeadlessEnvironment
    || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()
  )
  private val projectChangeOperation = AnonymousParallelOperationTrace(debugName = "Project change operation")
  private val projectReloadOperation = CompoundParallelOperationTrace<String>(debugName = "Project reload operation")
  private val dispatcher = MergingUpdateQueue("AutoImportProjectTracker.dispatcher", 300, false, null, project)
  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AutoImportProjectTracker.backgroundExecutor", 1)

  var isAsyncChangesProcessing by asyncChangesProcessingProperty

  private fun createProjectChangesListener() =
    object : ProjectBatchFileChangeListener(project) {
      override fun batchChangeStarted(activityName: String?) =
        projectChangeOperation.startTask()

      override fun batchChangeCompleted() =
        projectChangeOperation.finishTask()
    }

  private fun createProjectReloadListener(projectData: ProjectData) =
    object : ExternalSystemProjectListener {
      val id = "ProjectTracker: ${projectData.projectAware.projectId.debugName}"

      override fun onProjectReloadStart() {
        projectReloadOperation.startTask(id)
        projectData.status.markSynchronized(currentTime())
        projectData.isActivated = true
      }

      override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
        if (status != SUCCESS) projectData.status.markBroken(currentTime())
        projectReloadOperation.finishTask(id)
      }
    }

  override fun scheduleProjectRefresh() {
    LOG.debug("Schedule project reload", Throwable())
    schedule(priority = 0, dispatchIterations = 1) { reloadProject(smart = false) }
  }

  override fun scheduleChangeProcessing() {
    LOG.debug("Schedule change processing")
    schedule(priority = 1, dispatchIterations = 1) { processChanges() }
  }

  /**
   * ```
   * dispatcher.mergingTimeSpan = 300 ms
   * dispatchIterations = 9
   * We already dispatched processChanges
   * So delay is equal to (1 + 9) * 300 ms = 3000 ms = 3 s
   * ```
   */
  private fun scheduleDelayedSmartProjectReload() {
    LOG.debug("Schedule delayed project reload")
    schedule(priority = 2, dispatchIterations = 9) { reloadProject(smart = true) }
  }

  private fun schedule(priority: Int, dispatchIterations: Int, action: () -> Unit) {
    dispatcher.queue(PriorityEatUpdate(priority) {
      if (dispatchIterations - 1 > 0) {
        schedule(priority, dispatchIterations - 1, action)
      }
      else {
        action()
      }
    })
  }

  private fun processChanges() {
    when (settings.autoReloadType) {
      AutoReloadType.ALL -> when (getModificationType()) {
        INTERNAL -> scheduleDelayedSmartProjectReload()
        EXTERNAL -> scheduleDelayedSmartProjectReload()
        UNKNOWN -> updateProjectNotification()
      }
      AutoReloadType.SELECTIVE -> when (getModificationType()) {
        INTERNAL -> updateProjectNotification()
        EXTERNAL -> scheduleDelayedSmartProjectReload()
        UNKNOWN -> updateProjectNotification()
      }
      AutoReloadType.NONE -> updateProjectNotification()
    }
  }

  private fun reloadProject(smart: Boolean) {
    LOG.debug("Incremental project reload")

    val projectsToReload = projectDataMap.values
      .filter { (!smart || it.isActivated) && !it.isUpToDate() }

    if (isDisabledAutoReload() || projectsToReload.isEmpty()) {
      LOG.debug("Skipped all projects reload")
      updateProjectNotification()
      return
    }

    for (projectData in projectsToReload) {
      LOG.debug("${projectData.projectAware.projectId.debugName}: Project reload")
      val hasUndefinedModifications = !projectData.status.isUpToDate()
      val settingsContext = projectData.settingsTracker.getSettingsContext()
      val context = ProjectReloadContext(!smart, hasUndefinedModifications, settingsContext)
      projectData.projectAware.reloadProject(context)
    }
  }

  private fun updateProjectNotification() {
    LOG.debug("Notification status update")

    val isDisabledAutoReload = isDisabledAutoReload()
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
    for ((projectId, data) in projectDataMap) {
      when (isDisabledAutoReload || data.isUpToDate()) {
        true -> notificationAware.notificationExpire(projectId)
        else -> notificationAware.notificationNotify(data.projectAware)
      }
    }
  }

  private fun isDisabledAutoReload(): Boolean {
    return isDisabled.get() ||
           Registry.`is`("external.system.auto.import.disabled") ||
           !projectChangeOperation.isOperationCompleted() ||
           !projectReloadOperation.isOperationCompleted()
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
    val parentDisposable = Disposer.newDisposable(projectIdName)
    val settingsTracker = ProjectSettingsTracker(project, this, backgroundExecutor, projectAware, parentDisposable)
    val projectData = ProjectData(projectStatus, activationProperty, projectAware, settingsTracker, parentDisposable)
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)

    projectDataMap[projectId] = projectData

    val id = "ProjectSettingsTracker: $projectIdName"
    settingsTracker.beforeApplyChanges { projectReloadOperation.startTask(id) }
    settingsTracker.afterApplyChanges { projectReloadOperation.finishTask(id) }
    activationProperty.afterSet({ LOG.debug("$projectIdName is activated") }, parentDisposable)
    activationProperty.afterSet({ scheduleChangeProcessing() }, parentDisposable)

    Disposer.register(project, parentDisposable)
    projectAware.subscribe(createProjectReloadListener(projectData), parentDisposable)
    Disposer.register(parentDisposable, Disposable { notificationAware.notificationExpire(projectId) })

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
    projectData.status.markDirty(currentTime())
  }

  override fun markDirtyAllProjects() {
    val modificationTimeStamp = currentTime()
    projectDataMap.forEach { it.value.status.markDirty(modificationTimeStamp) }
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
    val projectSettingsTrackerStates = projectDataMap.asSequence()
      .map { (id, data) -> id.getState() to data.getState() }
      .toMap()
    return State(projectSettingsTrackerStates)
  }

  override fun loadState(state: State) {
    projectStates.putAll(state.projectSettingsTrackerStates)
    projectDataMap.forEach { (id, data) -> loadState(id, data) }
  }

  private fun loadState(projectId: ExternalSystemProjectId, projectData: ProjectData) {
    val projectState = projectStates.remove(projectId.getState())
    val settingsTrackerState = projectState?.settingsTracker
    if (settingsTrackerState == null || projectState.isDirty) {
      projectData.status.markDirty(currentTime(), EXTERNAL)
      scheduleChangeProcessing()
      return
    }
    projectData.settingsTracker.loadState(settingsTrackerState)
    projectData.settingsTracker.refreshChanges()
  }

  override fun initializeComponent() {
    LOG.debug("Project tracker initialization")
    ApplicationManager.getApplication().messageBus.connect(project).subscribe(BatchFileChangeListener.TOPIC, createProjectChangesListener())
    dispatcher.setRestartTimerOnAdd(true)
    dispatcher.isPassThrough = !isAsyncChangesProcessing
    dispatcher.activate()
  }

  @TestOnly
  fun getActivatedProjects() =
    projectDataMap.values
      .filter { it.isActivated }
      .map { it.projectAware.projectId }
      .toSet()

  /**
   * Enables auto-import in tests
   * Note: project tracker automatically enabled out of tests
   */
  @TestOnly
  fun enableAutoImportInTests() {
    isDisabled.set(false)
  }

  @TestOnly
  fun setDispatcherMergingSpan(delay: Int) {
    dispatcher.setMergingTimeSpan(delay)
  }

  init {
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
    projectReloadOperation.beforeOperation { LOG.debug("Project reload started") }
    projectReloadOperation.beforeOperation { notificationAware.notificationExpire() }
    projectReloadOperation.afterOperation { scheduleChangeProcessing() }
    projectReloadOperation.afterOperation { LOG.debug("Project reload finished") }
    projectChangeOperation.beforeOperation { LOG.debug("Project change started") }
    projectChangeOperation.beforeOperation { notificationAware.notificationExpire() }
    projectChangeOperation.afterOperation { scheduleChangeProcessing() }
    projectChangeOperation.afterOperation { LOG.debug("Project change finished") }
    settings.autoReloadTypeProperty.afterChange { scheduleChangeProcessing() }
    asyncChangesProcessingProperty.afterChange { dispatcher.isPassThrough = !it }
  }

  private fun ProjectData.getState() = State.Project(status.isDirty(), settingsTracker.getState())

  private fun ProjectSystemId.getState() = id

  private fun ExternalSystemProjectId.getState() = State.Id(systemId.getState(), externalProjectPath)

  private data class ProjectData(
    val status: ProjectStatus,
    val activationProperty: BooleanProperty,
    val projectAware: ExternalSystemProjectAware,
    val settingsTracker: ProjectSettingsTracker,
    val parentDisposable: Disposable
  ) {
    var isActivated by activationProperty

    fun isUpToDate() = status.isUpToDate() && settingsTracker.isUpToDate()

    fun getModificationType(): ExternalSystemModificationType {
      return ProjectStatus.merge(status.getModificationType(), settingsTracker.getModificationType())
    }
  }

  data class State(var projectSettingsTrackerStates: Map<Id, Project> = emptyMap()) {
    data class Id(var systemId: String? = null, var externalProjectPath: String? = null)
    data class Project(
      var isDirty: Boolean = false,
      var settingsTracker: ProjectSettingsTracker.State? = null
    )
  }

  private data class ProjectReloadContext(
    override val isExplicitReload: Boolean,
    override val hasUndefinedModifications: Boolean,
    override val settingsFilesContext: ExternalSystemSettingsFilesReloadContext
  ) : ExternalSystemProjectReloadContext

  companion object {
    val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")

    @TestOnly
    @JvmStatic
    fun getInstance(project: Project): AutoImportProjectTracker {
      return ExternalSystemProjectTracker.getInstance(project) as AutoImportProjectTracker
    }

    private val isDisabledAutoReload = AtomicBooleanProperty(
      ApplicationManager.getApplication().isUnitTestMode
    )

    @TestOnly
    fun enableAutoReloadInTests(parentDisposable: Disposable) {
      Disposer.register(parentDisposable) { isDisabledAutoReload.reset() }
      isDisabledAutoReload.set(false)
    }
  }
}