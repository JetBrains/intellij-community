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
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.PathUtilRt;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

public class ModuleDeleteProvider implements DeleteProvider, TitledHandler {
  public static ModuleDeleteProvider getInstance() {
    return ApplicationManager.getApplication().getService(ModuleDeleteProvider.class);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    List<UnloadedModuleDescription> unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext);
    return modules != null && !containsPrimaryModule(modules) || unloadedModules != null && !unloadedModules.isEmpty();
  }

  private static boolean containsPrimaryModule(Module[] modules) {
    if (!ProjectAttachProcessor.canAttachToProject()) {
      return !PlatformUtils.isIntelliJ();
    }

    for (Module module : modules) {
      String moduleFile = module.getModuleFilePath();
      Project project = module.getProject();
      if (!ProjectKt.isDirectoryBased(project)) {
        continue;
      }

      Path ideaDir = ProjectKt.getStateStore(project).getDirectoryStorePath();
      if (ideaDir != null && PathUtilRt.getParentPath(moduleFile).equals(FileUtil.toSystemIndependentName(ideaDir.toString()))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;

    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    List<UnloadedModuleDescription> unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext);

    Set<String> moduleNamesToDelete = getModuleNamesToDelete(modules, unloadedModules);
    String names = StringUtil.join(moduleNamesToDelete, name -> "'" + name + "'", ", ");
    String dialogTitle = StringUtil.trimEnd(getActionTitle(), "...");
    int ret = Messages.showOkCancelDialog(getConfirmationText(names, moduleNamesToDelete.size()), dialogTitle, CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
    if (ret != Messages.OK) return;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      final Runnable action = () -> {
        detachModules(project, modules, unloadedModules);
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

  private void detachModules(@NotNull Project project,
                             Module @Nullable [] modules,
                             @Nullable List<? extends UnloadedModuleDescription> unloadedModules) {
    doDetachModules(project, modules, unloadedModules);
  }

  public static void detachModules(@NotNull Project project, Module @Nullable [] modules) {
    getInstance().detachModules(project, modules, null);
  }

  protected @NlsContexts.DialogMessage String getConfirmationText(String names, int numberOfModules) {
    if (ProjectAttachProcessor.canAttachToProject()) {
      return ProjectBundle.message("project.remove.confirmation.prompt", names, numberOfModules);
    }
    return ProjectBundle.message("module.remove.confirmation.prompt", names, numberOfModules);
  }

  @Override
  public String getActionTitle() {
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