// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouperKt;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class NewModuleInGroupAction extends NewModuleAction {
  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    boolean mainMenu = ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    final ModuleGroup[] moduleGroups = e.getData(ModuleGroup.ARRAY_DATA_KEY);
    final Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    e.getPresentation().setVisible(!mainMenu && ((moduleGroups != null && moduleGroups.length > 0) ||
                                   (modules != null && modules.length > 0)));
  }

  @Override
  protected Object prepareDataFromContext(final AnActionEvent e) {
    final ModuleGroup[] moduleGroups = e.getData(ModuleGroup.ARRAY_DATA_KEY);
    if (moduleGroups != null && moduleGroups.length > 0) {
      return moduleGroups [0];
    }
    return null;
  }

  @Override
  protected void processCreatedModule(final Module module, final Object dataFromContext) {
    if (!ModuleGrouperKt.isQualifiedModuleNamesEnabled(module.getProject())) {
      ModuleGroup group = (ModuleGroup) dataFromContext;
      if (group != null) {
        WriteAction.run(() -> {
          ModifiableModuleModel modifiableModel = ModuleManager.getInstance(module.getProject()).getModifiableModel();
          modifiableModel.setModuleGroupPath(module, group.getGroupPath());
          modifiableModel.commit();
        });
      }
    }
  }
}
