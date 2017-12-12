/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.TitledHandler;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.PathUtilRt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleDeleteProvider  implements DeleteProvider, TitledHandler  {
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

      String ideaDir = ProjectKt.getStateStore(project).getDirectoryStorePath();
      if (PathUtilRt.getParentPath(moduleFile).equals(ideaDir)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;

    List<ModuleDescription> moduleDescriptions = new ArrayList<>();
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      moduleDescriptions.addAll(ContainerUtil.map(modules, LoadedModuleDescriptionImpl::new));
    }
    List<UnloadedModuleDescription> unloadedModules = ProjectView.UNLOADED_MODULES_CONTEXT_KEY.getData(dataContext);
    if (unloadedModules != null) {
      moduleDescriptions.addAll(unloadedModules);
    }

    String names = StringUtil.join(moduleDescriptions, description -> "\'" + description.getName() + "\'", ", ");
    int ret = Messages.showOkCancelDialog(getConfirmationText(names, moduleDescriptions.size()), getActionTitle(), Messages.getQuestionIcon());
    if (ret != Messages.OK) return;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      final Runnable action = () -> {
        final ModuleManager moduleManager = ModuleManager.getInstance(project);
        final Module[] currentModules = moduleManager.getModules();
        final ModifiableModuleModel modifiableModuleModel = moduleManager.getModifiableModel();
        final Map<Module, ModifiableRootModel> otherModuleRootModels = new HashMap<>();
        Set<String> moduleNamesToDelete = moduleDescriptions.stream().map(ModuleDescription::getName).collect(Collectors.toSet());
        for (final Module otherModule : currentModules) {
          if (!moduleNamesToDelete.contains(otherModule.getName())) {
            otherModuleRootModels.put(otherModule, ModuleRootManager.getInstance(otherModule).getModifiableModel());
          }
        }
        removeDependenciesOnModules(moduleNamesToDelete, otherModuleRootModels.values());
        if (modules != null) {
          for (final Module module : modules) {
            modifiableModuleModel.disposeModule(module);
          }
        }
        final ModifiableRootModel[] modifiableRootModels = otherModuleRootModels.values().toArray(new ModifiableRootModel[otherModuleRootModels.size()]);
        ModifiableModelCommitter.multiCommit(modifiableRootModels, modifiableModuleModel);
        if (unloadedModules != null) {
          moduleManager.removeUnloadedModules(unloadedModules);
        }
      };
      ApplicationManager.getApplication().runWriteAction(action);
    }, ProjectBundle.message("module.remove.command"), null);
  }

  private static String getConfirmationText(String names, int numberOfModules) {
    if (ProjectAttachProcessor.canAttachToProject()) {
      return ProjectBundle.message("project.remove.confirmation.prompt", names, numberOfModules);
    }
    return ProjectBundle.message("module.remove.confirmation.prompt", names, numberOfModules);
  }

  @Override
  public String getActionTitle() {
    return ProjectAttachProcessor.canAttachToProject() ? "Remove from Project View" : "Remove Module";
  }

  public static void removeModule(@NotNull final Module moduleToRemove,
                                  @NotNull Collection<ModifiableRootModel> otherModuleRootModels,
                                  @NotNull final ModifiableModuleModel moduleModel) {
    removeDependenciesOnModules(Collections.singleton(moduleToRemove.getName()), otherModuleRootModels);
    moduleModel.disposeModule(moduleToRemove);
  }

  private static void removeDependenciesOnModules(@NotNull Set<String> moduleNamesToRemove,
                                                  @NotNull Collection<ModifiableRootModel> otherModuleRootModels) {
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
