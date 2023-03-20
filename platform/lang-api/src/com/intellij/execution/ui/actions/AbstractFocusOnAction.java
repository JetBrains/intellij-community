// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public class AbstractFocusOnAction extends BaseViewAction implements Toggleable {

  private final @NotNull String myCondition;

  public AbstractFocusOnAction(@NotNull String condition) {
    myCondition = condition;
  }

  @Override
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean visible = content.length == 1;
    e.getPresentation().setVisible(visible);
    if (visible) {
      Toggleable.setSelected(e.getPresentation(), isToFocus(context, content));
    }
  }

  private boolean isToFocus(final ViewContext context, final Content[] content) {
    return context.getRunnerLayoutUi().getOptions().isToFocus(content[0], myCondition);
  }

  @Override
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean toFocus = isToFocus(context, content);
    context.getRunnerLayoutUi().getOptions().setToFocus(toFocus ? null : content[0], myCondition);
  }
}
