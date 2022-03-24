// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link CollapseAllAction} instead
 */
@Deprecated(forRemoval = true)
public abstract class TreeCollapseAllActionBase extends DumbAwareAction {
  protected abstract @Nullable TreeExpander getExpander(@NotNull DataContext context);

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    TreeExpander expander = getExpander(event.getDataContext());
    if (expander == null) return;
    if (!expander.canCollapse()) return;
    expander.collapseAll();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setVisible(expander == null || (expander.isCollapseAllVisible() && expander.isVisible(event)));
    presentation.setEnabled(expander != null && expander.canCollapse());
  }
}
