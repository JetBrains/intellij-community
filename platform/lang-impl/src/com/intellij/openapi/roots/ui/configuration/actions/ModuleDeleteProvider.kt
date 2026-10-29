// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.actions

import com.intellij.CommonBundle
import com.intellij.ide.DeleteProvider
import com.intellij.ide.TitledHandler
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ModuleAttachListener.Companion.TOPIC
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.project.stateStore
import com.intellij.projectImport.ProjectAttachProcessor.Companion.canAttachToProject
import com.intellij.util.Function
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.unregisterProjectRootBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

open class ModuleDeleteProvider : DeleteProvider, TitledHandler {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    val modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext)
    val unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext)
    return modules != null && modulesCanBeDeleted(modules) || !unloadedModules.isNullOrEmpty()
  }

  override fun deleteElement(dataContext: DataContext) {
    val project: Project = checkNotNull(CommonDataKeys.PROJECT.getData(dataContext))
    val modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext)
    val unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext)

    val moduleNamesToDelete = getModuleNamesToDelete(modules, unloadedModules)
    val names = StringUtil.join(moduleNamesToDelete, Function { name: String -> "'$name'" }, ", ")
    val dialogTitle = StringUtil.trimEnd(actionTitle, "...")
    val ret = Messages.showOkCancelDialog(
      getConfirmationText(names, moduleNamesToDelete.size), dialogTitle,
      CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(),
      Messages.getQuestionIcon()
    )
    if (ret != Messages.OK) return
    CommandProcessor.getInstance().executeCommand(project, Runnable {
      val action = Runnable {
        doDetachModules(project, modules, unloadedModules)
      }
      ApplicationManager.getApplication().runWriteAction(action)
    }, ProjectBundle.message("module.remove.command"), null)
  }

  protected open fun doDetachModules(
    project: Project,
    modules: Array<Module>?,
    unloadedModules: List<UnloadedModuleDescription>?,
  ) {
    val moduleManager = ModuleManager.getInstance(project)
    val currentModules = moduleManager.modules
    val modifiableModuleModel = moduleManager.getModifiableModel()
    val otherModuleRootModels: MutableMap<Module, ModifiableRootModel> = mutableMapOf()
    val moduleNamesToDelete = getModuleNamesToDelete(modules, unloadedModules)
    for (otherModule in currentModules) {
      if (!moduleNamesToDelete.contains(otherModule.getName())) {
        otherModuleRootModels[otherModule] = ModuleRootManager.getInstance(otherModule).getModifiableModel()
      }
    }
    removeDependenciesOnModules(moduleNamesToDelete, otherModuleRootModels.values)
    for (module in modules.orEmpty()) {
      project.getMessageBus().syncPublisher(TOPIC).beforeDetach(module)
      removeAttachedProjectRoots(module)
      modifiableModuleModel.disposeModule(module)
    }
    val modifiableRootModels = otherModuleRootModels.values.toTypedArray<ModifiableRootModel>()
    ModifiableModelCommitter.multiCommit(modifiableRootModels, modifiableModuleModel)
    if (unloadedModules != null) {
      moduleManager.removeUnloadedModules(unloadedModules)
    }
  }


  override fun getActionTitle(): String {
    return if (canAttachToProject())
      ProjectBundle.message("action.text.remove.from.project.view")
    else
      ProjectBundle.message("action.text.remove.module")
  }

  companion object {
    @JvmStatic
    fun getInstance(): ModuleDeleteProvider  {
      return ApplicationManager.getApplication().getService(ModuleDeleteProvider::class.java)
    }

    /**
     * Is it allowed to delete these modules?
     *
     *
     * Modules whose `.iml` lives outside `.idea` are always deletable.
     * Deletion is only blocked when the whole set of loaded project modules is selected and at least one of them
     * is stored in `.idea`: this stops a user from deleting all modules of a directory-based project at once.
     */
    private fun modulesCanBeDeleted(modulesToDelete: Array<Module>): Boolean {
      if (modulesToDelete.isEmpty()) {
        return false // No need to delete 0 modules
      }
      // All modules belong to the same project
      val project = modulesToDelete[0].getProject()
      val allProjectModules = project.modules

      val allModulesWillBeDeleted = modulesToDelete.size == allProjectModules.size
      if (allModulesWillBeDeleted) {
        // All modules can only be deleted if they are NOT in .idea dir
        val projectIdeaDir = project.stateStore.directoryStorePath
        if (projectIdeaDir != null) {
          for (module in modulesToDelete) {
            if (module.getModuleNioFile().startsWith(projectIdeaDir)) {
              return false // At least one module is in .idea, can't delete
            }
          }
        }
      }
      return true //Either not all modules are selected, or all of them are outside .idea dir -> deletable
    }

    private fun getModuleNamesToDelete(modules: Array<Module>?, unloadedModules: List<UnloadedModuleDescription>?): Set<String> {
      val moduleNamesToDelete: MutableSet<String> = mutableSetOf()
      for (module in modules.orEmpty()) {
        moduleNamesToDelete.add(module.getName())
      }
      if (unloadedModules != null) {
        for (unloadedModule in unloadedModules) {
          moduleNamesToDelete.add(unloadedModule.getName())
        }
      }
      return moduleNamesToDelete
    }

    private fun removeAttachedProjectRoots(module: Module) {
      val project = module.getProject()
      val snapshot: EntityStorage = WorkspaceModel.getInstance(project).currentSnapshot
      val moduleEntity = module.findModuleEntity(snapshot) ?: return
      for (contentRoot in moduleEntity.contentRoots) {
        val projectRoot =
          snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(contentRoot.url).filterIsInstance<ProjectRootEntity>().firstOrNull()
        if (projectRoot != null) {
          unregisterProjectRootBlocking(project, projectRoot.root)
        }
      }
    }

    @VisibleForTesting
    @ApiStatus.Internal
    fun detachModules(project: Project, modules: Array<Module>) {
      getInstance().doDetachModules(project, modules, null)
    }

    @NlsContexts.DialogMessage
    private fun getConfirmationText(names: String, numberOfModules: Int): @NlsContexts.DialogMessage String {
      if (canAttachToProject()) {
        return ProjectBundle.message("project.remove.confirmation.prompt", names, numberOfModules)
      }
      return ProjectBundle.message("module.remove.confirmation.prompt", names, numberOfModules)
    }

    private fun doRemoveModule(moduleToRemove: Module, otherModuleRootModels: Collection<ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
      removeDependenciesOnModules(mutableSetOf(moduleToRemove.getName()), otherModuleRootModels)
      moduleModel.disposeModule(moduleToRemove)
    }

    @JvmStatic
    fun removeModule(moduleToRemove: Module, otherModuleRootModels: Collection<ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
      doRemoveModule(moduleToRemove, otherModuleRootModels, moduleModel)
    }

    private fun removeDependenciesOnModules(moduleNamesToRemove: Set<String>, otherModuleRootModels: Collection<ModifiableRootModel>) {
      for (modifiableRootModel in otherModuleRootModels) {
        val orderEntries = modifiableRootModel.getOrderEntries()
        for (orderEntry in orderEntries) {
          if (orderEntry is ModuleOrderEntry && moduleNamesToRemove.contains(orderEntry.getModuleName())) {
            modifiableRootModel.removeOrderEntry(orderEntry)
          }
        }
      }
    }
  }
}