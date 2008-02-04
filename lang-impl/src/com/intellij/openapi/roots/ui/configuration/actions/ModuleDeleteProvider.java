package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModuleDeleteProvider  implements DeleteProvider  {
  public boolean canDeleteElement(DataContext dataContext) {
    return dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY) != null;
  }

  public void deleteElement(DataContext dataContext) {
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    assert modules != null;
    int ret = Messages.showOkCancelDialog(
      ProjectBundle.message("module.remove.confirmation.prompt", StringUtil.join(Arrays.asList(modules), new Function<Module, String>() {
        public String fun(final Module module) {
          return "\'" + module.getName() + "\'";
        }
      }, ", "), modules.length), ProjectBundle.message("module.remove.confirmation.title"), Messages.getQuestionIcon());
    if (ret != 0) return;
    CommandProcessor.getInstance().executeCommand(PlatformDataKeys.PROJECT.getData(dataContext), new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            for (Module module : modules) {
              removeModule(module);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, ProjectBundle.message("module.remove.command"), null);
  }

  public static void removeModule(final Module module) {
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
    Module[] modules = moduleManager.getModules();
    List<ModifiableRootModel> otherModuleRootModels = new ArrayList<ModifiableRootModel>();
    for (final Module otherModule : modules) {
      if (otherModule == module) continue;
      otherModuleRootModels.add(ModuleRootManager.getInstance(otherModule).getModifiableModel());
    }
    ModifiableModuleModel modifiableModuleModel = moduleManager.getModifiableModel();
    removeModule(module, modifiableModel, otherModuleRootModels, modifiableModuleModel);
    final ModifiableRootModel[] modifiableRootModels = otherModuleRootModels.toArray(new ModifiableRootModel[otherModuleRootModels.size()]);
    ProjectRootManager.getInstance(module.getProject()).multiCommit(modifiableModuleModel, modifiableRootModels);
  }

  public static void removeModule(final Module moduleToRemove,
                                     ModifiableRootModel modifiableRootModelToRemove,
                                     List<ModifiableRootModel> otherModuleRootModels,
                                     final ModifiableModuleModel moduleModel) {
    // remove all dependencies on the module that is about to be removed
    for (final ModifiableRootModel modifiableRootModel : otherModuleRootModels) {
      final OrderEntry[] orderEntries = modifiableRootModel.getOrderEntries();
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof ModuleOrderEntry && orderEntry.isValid()) {
          final Module orderEntryModule = ((ModuleOrderEntry)orderEntry).getModule();
          if (orderEntryModule != null && orderEntryModule.equals(moduleToRemove)) {
            modifiableRootModel.removeOrderEntry(orderEntry);
          }
        }
      }
    }
    // destroyProcess editor
    if (modifiableRootModelToRemove != null) {
      modifiableRootModelToRemove.dispose();
    }
    // destroyProcess module
    moduleModel.disposeModule(moduleToRemove);
  }
}
