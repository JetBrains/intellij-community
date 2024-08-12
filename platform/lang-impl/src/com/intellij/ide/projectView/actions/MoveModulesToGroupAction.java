// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveModulesToGroupAction extends AnAction {
  protected final ModuleGroup myModuleGroup;

  public MoveModulesToGroupAction(ModuleGroup moduleGroup, @ActionText String title) {
    super(title);
    myModuleGroup = moduleGroup;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    e.getPresentation().setEnabledAndVisible(modules != null);
    if (modules != null) {
      String description = IdeBundle.message("message.move.modules.to.group", whatToMove(modules), myModuleGroup.presentableText());
      presentation.setDescription(description);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected static String whatToMove(Module @NotNull [] modules) {
    return modules.length == 1 ? IdeBundle.message("message.module", modules[0].getName()) : IdeBundle.message("message.modules");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modules == null || modules.length == 0) return;
    doMove(modules, myModuleGroup, e.getDataContext());
  }

  public static void doMove(final Module @NotNull [] modules, final ModuleGroup group, final @Nullable DataContext dataContext) {
    Project project = modules[0].getProject();
    ModifiableModuleModel modifiableModuleModel = dataContext != null
                                  ? LangDataKeys.MODIFIABLE_MODULE_MODEL.getData(dataContext)
                                  : null;
    ModifiableModuleModel model =
      modifiableModuleModel != null ? modifiableModuleModel : ModuleManager.getInstance(project).getModifiableModel();
    for (final Module module : modules) {
      model.setModuleGroupPath(module, group == null ? null : group.getGroupPath());
    }
    if (modifiableModuleModel == null) {
      WriteAction.run(model::commit);
    }

    AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    if (pane != null) {
      pane.updateFromRoot(true);
    }

    if (!ProjectSettingsService.getInstance(project).processModulesMoved(modules, group) && pane != null) {
      if (group != null) {
        pane.selectModuleGroup(group, true);
      }
      else {
        pane.selectModule(modules[0], true);
      }
    }
  }
}