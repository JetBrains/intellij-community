// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectGroupActionGroup extends DefaultActionGroup implements DumbAware {
  private final ProjectGroup myGroup;

  public ProjectGroupActionGroup(@NotNull ProjectGroup group, @NotNull List<? extends AnAction> children) {
    super(group.getName(), children);
    myGroup = group;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setPopupGroup(!myGroup.isExpanded());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public @NotNull ProjectGroup getGroup() {
    return myGroup;
  }
}
