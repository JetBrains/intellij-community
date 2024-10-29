// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.actions.MoveModulesToGroupAction;
import com.intellij.ide.projectView.actions.MoveModulesToSubGroupAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class MoveModuleToGroup extends ActionGroup {
  private final ModuleGroup myModuleGroup;

  public MoveModuleToGroup(ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
    setPopup(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    boolean active = project != null && modules != null && modules.length != 0;
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(active);
    presentation.setText(StringUtil.escapeMnemonics(myModuleGroup.presentableText()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = getEventProject(e);
    if (project == null) return EMPTY_ARRAY;

    ModifiableModuleModel modifiableModuleModel = e.getData(LangDataKeys.MODIFIABLE_MODULE_MODEL);
    List<AnAction> result = new ArrayList<>();
    result.add(new MoveModulesToGroupAction(myModuleGroup, IdeBundle.message("action.move.module.to.this.group")));
    result.add(new MoveModulesToSubGroupAction(myModuleGroup));
    result.add(Separator.getInstance());
    ModuleGrouper grouper = ModuleGrouper.instanceFor(project, modifiableModuleModel);
    StreamEx.of(myModuleGroup.childGroups(grouper)).sorted((moduleGroup1, moduleGroup2) -> {
      assert moduleGroup1.getGroupPath().length == moduleGroup2.getGroupPath().length;
      return moduleGroup1.toString().compareToIgnoreCase(moduleGroup2.toString());
    }).map(MoveModuleToGroup::new).into(result);

    return result.toArray(AnAction.EMPTY_ARRAY);
  }
}