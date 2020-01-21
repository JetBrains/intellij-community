// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeExpandAllActionBase extends AnAction implements DumbAware {
  @Nullable
  protected abstract TreeExpander getExpander(DataContext dataContext);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    TreeExpander expander = getExpander(e.getDataContext());
    if (expander == null) return;
    if (!expander.canExpand()) return;
    expander.expandAll();
  }

  @Override
  public final void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setVisible(expander == null || expander.isVisible(event));
    presentation.setEnabled(expander != null && expander.canExpand());
  }
}
