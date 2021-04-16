// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.configuration.ConfigureUnloadedModulesDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.xml.util.XmlStringUtil

/**
 * If some modules were unloaded and new modules appears after loading project configuration, automatically unloads those which
 * aren't required for loaded modules.
 */
@State(name = "AutomaticModuleUnloader", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
@Service
class AutomaticModuleUnloader(private val project: Project) : SimplePersistentStateComponent<LoadedModulesListStorage>(LoadedModulesListStorage()) {
  fun processNewModules(modulesToLoad: Set<ModulePath>, modulesToUnload: List<UnloadedModuleDescriptionImpl>): UnloadedModulesListChange {
    val oldLoaded = state.modules.toSet()
    if (oldLoaded.isEmpty() || modulesToLoad.all { it.moduleName in oldLoaded }) {
      return UnloadedModulesListChange(emptyList(), emptyList(), emptyList())
    }

    val moduleDescriptions = LinkedHashMap<String, UnloadedModuleDescriptionImpl>(modulesToLoad.size + modulesToUnload.size)
    UnloadedModuleDescriptionImpl.createFromPaths(modulesToLoad, project).associateByTo(moduleDescriptions) { it.name }
    modulesToUnload.associateByTo(moduleDescriptions) { it.name }

    val oldLoadedWithDependencies = HashSet<ModuleDescription>()
    val explicitlyUnloaded = modulesToUnload.mapTo(HashSet()) { it.name }
    for (name in oldLoaded) {
      processTransitiveDependencies(name, moduleDescriptions, explicitlyUnloaded, oldLoadedWithDependencies)
    }

    val newLoadedNames = oldLoadedWithDependencies.mapTo(LinkedHashSet()) { it.name }
    val toLoad = modulesToLoad.filter { it.moduleName in newLoadedNames && it.moduleName !in oldLoaded}
    val toUnload = modulesToLoad.filter { it.moduleName !in newLoadedNames && it.moduleName in moduleDescriptions}
    state.modules.clear()
    modulesToLoad.asSequence()
      .filter { it.moduleName in newLoadedNames }
      .mapTo(state.modules) { it.moduleName }
    val change = UnloadedModulesListChange(toLoad, toUnload, toUnload.map { moduleDescriptions[it.moduleName]!! })
    fireNotifications(change.toLoad.map { it.moduleName }, change.toUnload.map { it.moduleName })
    return change
  }

  fun processNewModules(currentModules: Set<String>, storage: WorkspaceEntityStorage) {
    if (currentModules.isEmpty()) return

    val oldLoaded = state.modules.toSet()
    //if we don't store list of loaded modules most probably it means that the project wasn't opened on this machine, so let's not unload all modules
    if (oldLoaded.isEmpty()) return

    val unloadedStorage = UnloadedModulesListStorage.getInstance(project)
    val unloadedModules = unloadedStorage.unloadedModuleNames.toSet()
    //if no modules were unloaded by user, automatic unloading shouldn't start
    if (unloadedModules.isEmpty()) return

    //no new modules were added, nothing to process
    if (currentModules.all { it in oldLoaded || it in unloadedModules }) return

    val oldLoadedWithDependencies = HashSet<String>()
    for (name in oldLoaded) {
      processTransitiveDependencies(ModuleId(name), storage, unloadedModules, oldLoadedWithDependencies)
    }

    val toLoad = currentModules.filter { it in oldLoadedWithDependencies && it !in oldLoaded }
    val toUnload = currentModules.filter { it !in oldLoadedWithDependencies && it !in unloadedModules}
    if (toUnload.isNotEmpty()) {
      LOG.info("Old loaded modules: $oldLoaded")
      LOG.info("Old unloaded modules: $unloadedModules")
      LOG.info("New modules to unload: $toUnload")
    }
    fireNotifications(toLoad, toUnload)
    unloadedStorage.unloadedModuleNames = unloadedStorage.unloadedModuleNames + toUnload
  }

  private fun processTransitiveDependencies(moduleId: ModuleId, storage: WorkspaceEntityStorage, explicitlyUnloaded: Set<String>,
                                            result: MutableSet<String>) {
    if (moduleId.name in explicitlyUnloaded) return
    val moduleEntity = storage.resolve(moduleId) ?: return
    if (!result.add(moduleEntity.name)) return
    moduleEntity.dependencies.forEach {
      if (it is ModuleDependencyItem.Exportable.ModuleDependency) {
        processTransitiveDependencies(it.module, storage, explicitlyUnloaded, result)
      }
    }
  }

  private fun processTransitiveDependencies(name: String, moduleDescriptions: Map<String, UnloadedModuleDescriptionImpl>,
                                            explicitlyUnloaded: Set<String>, result: MutableSet<ModuleDescription>) {
    if (name in explicitlyUnloaded) return

    val module = moduleDescriptions[name]
    if (module == null || !result.add(module)) return

    module.dependencyModuleNames.forEach {
      processTransitiveDependencies(it, moduleDescriptions, explicitlyUnloaded, result)
    }
  }

  private fun fireNotifications(modulesToLoad: List<String>, modulesToUnload: List<String>) {
    if (modulesToLoad.isEmpty() && modulesToUnload.isEmpty()) return

    val messages = mutableListOf<String>()
    val actions = mutableListOf<NotificationAction>()

    if (modulesToUnload.isNotEmpty()) {
      val args = arrayOf(modulesToUnload.size, modulesToUnload[0], if (modulesToUnload.size == 2) modulesToUnload[1] else modulesToUnload.size - 1)
      messages += ProjectBundle.message("auto.unloaded.notification", *args)
      val text = ProjectBundle.message(if (modulesToUnload.isEmpty()) "auto.unloaded.revert.short" else "auto.unloaded.revert.full", *args)
      actions += createAction(text) { list -> list.removeAll(modulesToUnload) }
    }

    if (modulesToLoad.isNotEmpty()) {
      val args = arrayOf(modulesToLoad.size, modulesToLoad[0], if (modulesToLoad.size == 2) modulesToLoad[1] else modulesToLoad.size - 1)
      messages += ProjectBundle.message("auto.loaded.notification", *args)
      val action = ProjectBundle.message(if (modulesToUnload.isEmpty()) "auto.loaded.revert.short" else "auto.loaded.revert.full", *args)
      actions += createAction(action) { list -> list.addAll(modulesToLoad) }
    }

    actions += object : NotificationAction(ProjectBundle.message("configure.unloaded.modules")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        if (ConfigureUnloadedModulesDialog(project, null).showAndGet()) {
          notification.expire()
        }
      }
    }

    val content = XmlStringUtil.wrapInHtml(messages.joinToString("<br>"))
    val notification = NOTIFICATION_GROUP.createNotification(ProjectBundle.message("modules.added.notification.title"), content, NotificationType.INFORMATION, null)
    notification.addActions(actions)
    notification.notify(project)
  }

  fun createAction(@NlsContexts.NotificationContent text: String, action: (MutableList<String>) -> Unit): NotificationAction = object : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      val unloaded = ArrayList<String>()
      val moduleManager = ModuleManager.getInstance(project)
      moduleManager.unloadedModuleDescriptions.mapTo(unloaded) { it.name }
      action(unloaded)
      moduleManager.setUnloadedModules(unloaded)
      notification.expire()
    }
  }

  fun setLoadedModules(modules: List<String>) {
    val list = state.modules
    list.clear()
    list.addAll(modules)
    // compiler uses module list from disk, ask to save workspace
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project, forceSavingAllSettings = true)
  }

  companion object {
    private val LOG = logger<AutomaticModuleUnloader>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<AutomaticModuleUnloader>()

    private val NOTIFICATION_GROUP: NotificationGroup
      get() = NotificationGroupManager.getInstance().getNotificationGroup("Automatic Module Unloading")
  }
}

class LoadedModulesListStorage : BaseState() {
  @get:XCollection(elementName = "module", valueAttributeName = "name", propertyElementName = "loaded-modules")
  val modules by list<String>()
}

class UnloadedModulesListChange(val toLoad: List<ModulePath>, val toUnload: List<ModulePath>, val toUnloadDescriptions: List<UnloadedModuleDescriptionImpl>)
