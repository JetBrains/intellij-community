// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.AutomaticModuleUnloader
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.configuration.ConfigureUnloadedModulesDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.workspaceModel.ide.UnloadedModulesNameHolder
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.getModuleLevelLibraries
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.launch

/**
 * If some modules were unloaded and new modules appears after loading project configuration, automatically unloads those which
 * aren't required for loaded modules.
 */
@State(name = "AutomaticModuleUnloader", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
internal class AutomaticModuleUnloaderImpl(private val project: Project) : SimplePersistentStateComponent<LoadedModulesListStorage>(LoadedModulesListStorage()),
                                                                           AutomaticModuleUnloader {
  override fun processNewModules(currentModules: Set<String>, builder: MutableEntityStorage, unloadedEntityBuilder: MutableEntityStorage) {
    if (currentModules.isEmpty()) {
      return
    }

    val oldLoaded = state.modules.toHashSet()
    // if we don't store list of loaded modules most probably it means that the project wasn't opened on this machine,
    // so let's not unload all modules
    if (oldLoaded.isEmpty()) {
      return
    }

    val unloadedStorage = UnloadedModulesListStorage.getInstance(project)
    val unloadedModulesHolder = unloadedStorage.unloadedModuleNameHolder
    // if no modules were unloaded by user, automatic unloading shouldn't start
    if (!unloadedModulesHolder.hasUnloaded()) {
      return
    }

    //no new modules were added, nothing to process
    if (currentModules.all { it in oldLoaded || unloadedModulesHolder.isUnloaded(it) }) {
      return
    }

    val oldLoadedWithDependencies = HashSet<String>()
    for (name in oldLoaded) {
      processTransitiveDependencies(ModuleId(name), builder, unloadedEntityBuilder, unloadedModulesHolder, oldLoadedWithDependencies)
    }

    val toLoad = currentModules.filter { it in oldLoadedWithDependencies && it !in oldLoaded }
    val toUnload = currentModules.filter { it !in oldLoadedWithDependencies && !unloadedModulesHolder.isUnloaded(it)}
    if (toUnload.isNotEmpty()) {
      LOG.info("Old loaded modules: $oldLoaded")
      LOG.info("Old unloaded modules: $unloadedModulesHolder")
      LOG.info("New modules to unload: $toUnload")
    }
    fireNotifications(toLoad, toUnload)
    unloadedStorage.addUnloadedModuleNames(toUnload)
    if (toUnload.isNotEmpty()) {
      val toUnloadSet = toUnload.toSet()
      /* we need to create a snapshot because adding entities from one builder to another will result
         in "Entity is already created in a different builder" exception */
      val snapshot = builder.toSnapshot()
      val moduleEntitiesToAdd = snapshot.entities(ModuleEntity::class.java).filter { it.name in toUnloadSet }.toList()
      val moduleEntitiesToRemove = builder.entities(ModuleEntity::class.java).filter { it.name in toUnloadSet }.toList()
      for (moduleEntity in moduleEntitiesToAdd) {
        unloadedEntityBuilder.addEntity(moduleEntity)
        moduleEntity.getModuleLevelLibraries(snapshot).forEach { libraryEntity ->
          unloadedEntityBuilder.addEntity(libraryEntity)
        }
      }
      for (moduleEntity in moduleEntitiesToRemove) {
        builder.removeEntity(moduleEntity)
      }
    }
  }

  private fun processTransitiveDependencies(moduleId: ModuleId, storage: EntityStorage, unloadedEntitiesStorage: EntityStorage,
                                            explicitlyUnloadedHolder: UnloadedModulesNameHolder, result: MutableSet<String>) {
    if (explicitlyUnloadedHolder.isUnloaded(moduleId.name)) return
    val moduleEntity = storage.resolve(moduleId) ?: unloadedEntitiesStorage.resolve(moduleId) ?: return
    if (!result.add(moduleEntity.name)) return
    moduleEntity.dependencies.forEach {
      if (it is ModuleDependencyItem.Exportable.ModuleDependency) {
        processTransitiveDependencies(it.module, storage, unloadedEntitiesStorage, explicitlyUnloadedHolder, result)
      }
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
    NOTIFICATION_GROUP
      .createNotification(ProjectBundle.message("modules.added.notification.title"), content, NotificationType.INFORMATION)
      .addActions(actions as Collection<AnAction>)
      .notify(project)
  }

  fun createAction(@NlsContexts.NotificationContent text: String, action: (MutableList<String>) -> Unit): NotificationAction = object : NotificationAction(text) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      val unloaded = ArrayList<String>()
      val moduleManager = ModuleManager.getInstance(project)
      moduleManager.unloadedModuleDescriptions.mapTo(unloaded) { it.name }
      action(unloaded)
      project.coroutineScope.launch {
        moduleManager.setUnloadedModules(unloaded)
      }
      notification.expire()
    }
  }

  override fun setLoadedModules(modules: List<String>) {
    val list = state.modules
    list.clear()
    list.addAll(modules)
    // compiler uses module list from disk, ask to save workspace
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project, forceSavingAllSettings = true)
  }

  companion object {
    private val LOG = logger<AutomaticModuleUnloaderImpl>()

    private val NOTIFICATION_GROUP: NotificationGroup
      get() = NotificationGroupManager.getInstance().getNotificationGroup("Automatic Module Unloading")
  }
}

class LoadedModulesListStorage : BaseState() {
  @get:XCollection(elementName = "module", valueAttributeName = "name", propertyElementName = "loaded-modules")
  val modules by list<String>()
}