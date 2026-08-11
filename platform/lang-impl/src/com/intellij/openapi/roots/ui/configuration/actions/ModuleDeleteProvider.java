// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.TitledHandler;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectAttachProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleDeleteProvider implements DeleteProvider, TitledHandler {
  public static ModuleDeleteProvider getInstance() {
    return ApplicationManager.getApplication().getService(ModuleDeleteProvider.class);
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final boolean canDeleteElement(@NotNull DataContext dataContext) {
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    List<UnloadedModuleDescription> unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext);
    return modules != null && modulesCanBeDeleted(modules) || unloadedModules != null && !unloadedModules.isEmpty();
  }

  /**
   * Is it allowed to delete these modules?
   * <p>
   * Modules whose {@code .iml} lives outside {@code .idea} are always deletable.
   * Deletion is only blocked when the whole set of loaded project modules is selected and at least one of them
   * is stored in {@code .idea}: this stops a user from deleting all modules of a directory-based project at once.
   */
  private static boolean modulesCanBeDeleted(Module @NotNull [] modulesToDelete) {
    if (modulesToDelete.length == 0) {
      return false; // No need to delete 0 modules
    }
    // All modules belong to the same project
    var project = modulesToDelete[0].getProject();
    var allProjectModules = ProjectUtil.getModules(project);

    var allModulesWillBeDeleted = modulesToDelete.length == allProjectModules.length;
    if (allModulesWillBeDeleted) {
      // All modules can only be deleted if they are NOT in .idea dir
      @Nullable
      var projectIdeaDir = ProjectKt.getStateStore(project).getDirectoryStorePath();
      if (projectIdeaDir != null) {
        for (Module module : modulesToDelete) {
          if (module.getModuleNioFile().startsWith(projectIdeaDir)) {
            return false; // At least one module is in .idea, can't delete
          }
        }
      }
    }
    return true; //Either not all modules are selected, or all of them are outside .idea dir -> deletable
  }

  @Override
  public final void deleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;

    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    List<UnloadedModuleDescription> unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext);

    Set<String> moduleNamesToDelete = getModuleNamesToDelete(modules, unloadedModules);
    String names = StringUtil.join(moduleNamesToDelete, name -> "'" + name + "'", ", ");
    String dialogTitle = StringUtil.trimEnd(getActionTitle(), "...");
    int ret = Messages.showOkCancelDialog(getConfirmationText(names, moduleNamesToDelete.size()), dialogTitle,
                                          CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(),
                                          Messages.getQuestionIcon());
    if (ret != Messages.OK) return;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      final Runnable action = () -> {
        doDetachModules(project, modules, unloadedModules);
      };
      ApplicationManager.getApplication().runWriteAction(action);
    }, ProjectBundle.message("module.remove.command"), null);
  }

  private static Set<String> getModuleNamesToDelete(Module @Nullable [] modules,
                                                    @Nullable List<? extends UnloadedModuleDescription> unloadedModules) {
    Set<String> moduleNamesToDelete = new HashSet<>();
    if (null != modules) {
      for (var module : modules) {
        moduleNamesToDelete.add(module.getName());
      }
    }
    if (unloadedModules != null) {
      for (var unloadedModule : unloadedModules) {
        moduleNamesToDelete.add(unloadedModule.getName());
      }
    }
    return moduleNamesToDelete;
  }

  protected void doDetachModules(@NotNull Project project,
                                 Module @Nullable [] modules,
                                 @Nullable List<? extends UnloadedModuleDescription> unloadedModules) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] currentModules = moduleManager.getModules();
    final ModifiableModuleModel modifiableModuleModel = moduleManager.getModifiableModel();
    final Map<Module, ModifiableRootModel> otherModuleRootModels = new HashMap<>();
    Set<String> moduleNamesToDelete = getModuleNamesToDelete(modules, unloadedModules);
    for (final Module otherModule : currentModules) {
      if (!moduleNamesToDelete.contains(otherModule.getName())) {
        otherModuleRootModels.put(otherModule, ModuleRootManager.getInstance(otherModule).getModifiableModel());
      }
    }
    removeDependenciesOnModules(moduleNamesToDelete, otherModuleRootModels.values());
    if (modules != null) {
      ProjectAttachProcessor attachProcessor = ProjectAttachProcessor.getProcessor(null, null, null);
      for (final Module module : modules) {
        if (attachProcessor != null) {
          attachProcessor.beforeDetach(module);
        }
        modifiableModuleModel.disposeModule(module);
      }
    }
    final ModifiableRootModel[] modifiableRootModels = otherModuleRootModels.values().toArray(new ModifiableRootModel[0]);
    ModifiableModelCommitter.multiCommit(modifiableRootModels, modifiableModuleModel);
    if (unloadedModules != null) {
      moduleManager.removeUnloadedModules(unloadedModules);
    }
  }


  @VisibleForTesting
  @ApiStatus.Internal
  public static void detachModules(@NotNull Project project, Module @Nullable [] modules) {
    getInstance().doDetachModules(project, modules, null);
  }

  private static @NlsContexts.DialogMessage String getConfirmationText(@NotNull String names, int numberOfModules) {
    if (ProjectAttachProcessor.canAttachToProject()) {
      return ProjectBundle.message("project.remove.confirmation.prompt", names, numberOfModules);
    }
    return ProjectBundle.message("module.remove.confirmation.prompt", names, numberOfModules);
  }

  @Override
  public final String getActionTitle() {
    return ProjectAttachProcessor.canAttachToProject() ? ProjectBundle.message("action.text.remove.from.project.view")
                                                       : ProjectBundle.message("action.text.remove.module");
  }

  private static void doRemoveModule(final @NotNull Module moduleToRemove,
                                     @NotNull Collection<? extends ModifiableRootModel> otherModuleRootModels,
                                     final @NotNull ModifiableModuleModel moduleModel) {
    removeDependenciesOnModules(Collections.singleton(moduleToRemove.getName()), otherModuleRootModels);
    moduleModel.disposeModule(moduleToRemove);
  }

  public static void removeModule(final @NotNull Module moduleToRemove,
                                  @NotNull Collection<? extends ModifiableRootModel> otherModuleRootModels,
                                  final @NotNull ModifiableModuleModel moduleModel) {
    doRemoveModule(moduleToRemove, otherModuleRootModels, moduleModel);
  }

  private static void removeDependenciesOnModules(@NotNull Set<String> moduleNamesToRemove,
                                                  @NotNull Collection<? extends ModifiableRootModel> otherModuleRootModels) {
    for (final ModifiableRootModel modifiableRootModel : otherModuleRootModels) {
      final OrderEntry[] orderEntries = modifiableRootModel.getOrderEntries();
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof ModuleOrderEntry && moduleNamesToRemove.contains(((ModuleOrderEntry)orderEntry).getModuleName())) {
          modifiableRootModel.removeOrderEntry(orderEntry);
        }
      }
    }
  }
}