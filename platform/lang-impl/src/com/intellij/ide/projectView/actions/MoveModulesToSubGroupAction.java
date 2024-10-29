// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class MoveModulesToSubGroupAction extends MoveModulesToGroupAction {
  public MoveModulesToSubGroupAction(ModuleGroup moduleGroup) {
    super(moduleGroup, moduleGroup == null ? IdeBundle.message("action.move.module.new.top.level.group") : IdeBundle.message("action.move.module.to.new.sub.group"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) != null);
    String description = IdeBundle.message("action.description.create.new.module.group");
    presentation.setDescription(description);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modules == null || modules.length == 0) return;
    List<String> newGroup;
    if (myModuleGroup != null) {
      String message = IdeBundle.message("prompt.specify.name.of.module.subgroup", myModuleGroup.presentableText(), whatToMove(modules));
      String subgroup = Messages.showInputDialog(message, IdeBundle.message("title.module.sub.group"), Messages.getQuestionIcon());
      if (subgroup == null || subgroup.trim().isEmpty()) return;
      newGroup = ContainerUtil.append(myModuleGroup.getGroupPathList(), subgroup);
    }
    else {
      String message = IdeBundle.message("prompt.specify.module.group.name", whatToMove(modules));
      String group = Messages.showInputDialog(message, IdeBundle.message("title.module.group"), Messages.getQuestionIcon());
      if (group == null || group.trim().isEmpty()) return;
      newGroup = Collections.singletonList(group);
    }

    doMove(modules, new ModuleGroup(newGroup), e.getDataContext());
  }
}