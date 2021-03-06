// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.*
import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.configuration.ConfigureUnloadedModulesDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.xmlb.annotations.XCollection
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
    fireNotifications(change)
    return change
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

  private fun fireNotifications(change: UnloadedModulesListChange) {
    if (change.toLoad.isEmpty() && change.toUnload.isEmpty()) return

    val messages = mutableListOf<String>()
    val actions = mutableListOf<NotificationAction>()

    if (change.toUnload.isNotEmpty()) {
      val modules = change.toUnload
      val args = arrayOf(modules.size, modules[0].moduleName, modules.getOrElse(2) { modules.size - 1 })
      messages += ProjectBundle.message("auto.unloaded.notification", *args)
      val text = ProjectBundle.message(if (change.toUnload.isEmpty()) "auto.unloaded.revert.short" else "auto.unloaded.revert.full", *args)
      actions += createAction(text) { list -> list.removeAll(modules.map { it.moduleName }) }
    }

    if (change.toLoad.isNotEmpty()) {
      val modules = change.toLoad
      val args = arrayOf(modules.size, modules[0].moduleName, modules.getOrElse(2) { modules.size - 1 })
      messages += ProjectBundle.message("auto.loaded.notification", *args)
      val action = ProjectBundle.message(if (change.toUnload.isEmpty()) "auto.loaded.revert.short" else "auto.loaded.revert.full", *args)
      actions += createAction(action) { list -> list.addAll(modules.map { it.moduleName }) }
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
