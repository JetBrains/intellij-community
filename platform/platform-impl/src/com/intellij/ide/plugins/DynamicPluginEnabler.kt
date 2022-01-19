// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.application.options.RegistryManager
import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.project.stateStore
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@State(
  name = "DynamicPluginEnabler",
  storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
  reloadable = false,
)
@ApiStatus.Internal
class DynamicPluginEnabler : SimplePersistentStateComponent<DynamicPluginEnablerState>(DynamicPluginEnablerState()), PluginEnabler {
  companion object {
    private val isPerProjectEnabledValue: RegistryValue
      get() = RegistryManager.getInstance().get("ide.plugins.per.project")

    @JvmStatic
    var isPerProjectEnabled: Boolean
      get() = isPerProjectEnabledValue.asBoolean()
      set(value) = isPerProjectEnabledValue.setValue(value)

    @JvmStatic
    @JvmOverloads
    fun findPluginTracker(project: Project, pluginEnabler: PluginEnabler = PluginEnabler.getInstance()): ProjectPluginTracker? {
      return (pluginEnabler as? DynamicPluginEnabler)?.getPluginTracker(project)
    }

    internal class EnableDisablePluginsActivity : StartupActivity {
      private val dynamicPluginEnabler = PluginEnabler.getInstance() as? DynamicPluginEnabler
                                         ?: throw ExtensionNotApplicableException.create()

      override fun runActivity(project: Project) {
        val tracker = dynamicPluginEnabler.getPluginTracker(project)
        val projects = openProjectsExcludingCurrent(project)

        val pluginIdsToLoad = tracker.enabledPluginsIds
          .union(dynamicPluginEnabler.locallyDisabledAndGloballyEnabledPlugins(projects))

        val pluginIdsToUnload = tracker.disabledPluginsIds

        if (pluginIdsToLoad.isNotEmpty() || pluginIdsToUnload.isNotEmpty()) {
          val indicator = ProgressManager.getInstance().progressIndicator
          ApplicationManager.getApplication().invokeAndWait {
            indicator?.let {
              it.text = IdeBundle.message("plugins.progress.loading.plugins.for.current.project.title", project.name)
            }
            DynamicPlugins.loadPlugins(pluginIdsToLoad.toPluginDescriptors())

            indicator?.let {
              it.text = IdeBundle.message("plugins.progress.unloading.plugins.for.current.project.title", project.name)
            }
            dynamicPluginEnabler.unloadPlugins(
              pluginIdsToUnload.toPluginDescriptors(),
              project,
              projects,
            )
          }
        }
      }
    }

    private fun openProjectsExcludingCurrent(project: Project?) = ProjectManager.getInstance().openProjects.filterNot { it == project }
  }

  val trackers: Map<String, ProjectPluginTracker>
    get(): Map<String, ProjectPluginTracker> = state.trackers

  private var applicationShuttingDown = false

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosing(project: Project) {
        if (applicationShuttingDown) {
          return
        }

        val tracker = getPluginTracker(project)
        val projects = openProjectsExcludingCurrent(project)

        val descriptorsToLoad = if (projects.isNotEmpty()) {
          emptyList()
        }
        else {
          tracker.disabledPluginsIds
            .filterNot { isDisabled(it) }
            .toPluginDescriptors()
        }
        DynamicPlugins.loadPlugins(descriptorsToLoad)

        val descriptorsToUnload = tracker.enabledPluginsIds
          .union(locallyDisabledPlugins(projects))
          .toPluginDescriptors()

        unloadPlugins(
          descriptorsToUnload,
          project,
          projects,
        )
      }
    })

    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        applicationShuttingDown = true
      }
    })
  }

  fun getPluginTracker(project: Project): ProjectPluginTracker = state.findStateByProject(project)

  override fun isDisabled(pluginId: PluginId) = PluginEnabler.HEADLESS.isDisabled(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>) = updatePluginsState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>) = updatePluginsState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY)

  @ApiStatus.Internal
  @JvmOverloads
  fun updatePluginsState(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    assert(!action.isPerProject || project != null)

    val pluginIds = descriptors.toPluginSet()

    fun unloadExcessPlugins() = unloadPlugins(
      descriptors,
      project,
      openProjectsExcludingCurrent(project),
      parentComponent,
    )

    PluginManagerUsageCollector.pluginsStateChanged(descriptors, action, project)
    return when (action) {
      PluginEnableDisableAction.ENABLE_GLOBALLY -> {
        state.stopTracking(pluginIds)
        val loaded = DynamicPlugins.loadPlugins(descriptors)
        PluginEnabler.HEADLESS.enableById(pluginIds)
        loaded
      }
      PluginEnableDisableAction.ENABLE_FOR_PROJECT -> {
        state.startTracking(project!!, pluginIds, true)
        DynamicPlugins.loadPlugins(descriptors)
      }
      PluginEnableDisableAction.ENABLE_FOR_PROJECT_DISABLE_GLOBALLY -> {
        PluginEnabler.HEADLESS.disableById(pluginIds)
        descriptors.forEach { it.isEnabled = true }
        state.startTracking(project!!, pluginIds, true)
        true
      }
      PluginEnableDisableAction.DISABLE_GLOBALLY -> {
        PluginEnabler.HEADLESS.disableById(pluginIds)
        state.stopTracking(pluginIds)
        unloadExcessPlugins()
      }
      PluginEnableDisableAction.DISABLE_FOR_PROJECT -> {
        state.startTracking(project!!, pluginIds, false)
        unloadExcessPlugins()
      }
      PluginEnableDisableAction.DISABLE_FOR_PROJECT_ENABLE_GLOBALLY ->
        false
    }
  }

  private fun unloadPlugins(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project?,
    projects: List<Project>,
    parentComponent: JComponent? = null,
  ): Boolean {
    val predicate = when (project) {
      null -> { _: PluginId -> true }
      else -> shouldUnload(projects)
    }

    return DynamicPlugins.unloadPlugins(
      descriptors.filter { predicate(it.pluginId) },
      project,
      parentComponent,
    )
  }

  private fun locallyDisabledPlugins(projects: List<Project>): Collection<PluginId> {
    return projects
      .map { getPluginTracker(it) }
      .flatMap { it.disabledPluginsIds }
  }

  private fun locallyDisabledAndGloballyEnabledPlugins(projects: List<Project>): Collection<PluginId> {
    return locallyDisabledPlugins(projects)
      .filterNot { isDisabled(it) }
  }

  private fun shouldUnload(openProjects: List<Project>) = object : (PluginId) -> Boolean {

    private val trackers = openProjects
      .map { getPluginTracker(it) }

    private val requiredPluginIds = openProjects
      .map { ExternalDependenciesManager.getInstance(it) }
      .flatMap { it.getDependencies(DependencyOnPlugin::class.java) }
      .mapTo(HashSet()) { it.pluginId }

    override fun invoke(pluginId: PluginId): Boolean {
      return !requiredPluginIds.contains(pluginId.idString) &&
             !trackers.any { it.isEnabled(pluginId) } &&
             (isDisabled(pluginId) || trackers.all { it.isDisabled(pluginId) })
    }
  }
}

@ApiStatus.Internal
class DynamicPluginEnablerState : BaseState() {
  @get:XCollection(propertyElementName = "trackers", style = XCollection.Style.v2)
  internal val trackers by map<String, ProjectPluginTracker>()

  fun startTracking(project: Project, pluginIds: Collection<PluginId>, enable: Boolean) {
    val updated = findStateByProject(project)
      .startTracking(pluginIds, enable)

    if (updated) {
      incrementModificationCount()
    }
  }

  fun stopTracking(pluginIds: Collection<PluginId>) {
    var updated = false

    trackers.values.forEach {
      updated = it.stopTracking(pluginIds) || updated
    }

    if (updated) {
      incrementModificationCount()
    }
  }

  internal fun findStateByProject(project: Project): ProjectPluginTracker {
    val workspaceId = if (project.isDefault) null else project.stateStore.projectWorkspaceId
    val projectName = project.name
    return trackers.computeIfAbsent(workspaceId ?: projectName) {
      ProjectPluginTracker().also {
        it.projectName = projectName
        incrementModificationCount()
      }
    }
  }
}