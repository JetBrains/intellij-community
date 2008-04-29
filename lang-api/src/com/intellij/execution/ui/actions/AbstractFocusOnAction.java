package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.ui.content.Content;

public class AbstractFocusOnAction extends BaseViewAction implements Toggleable {
  private String myCondition;

  public AbstractFocusOnAction(String condition) {
    myCondition = condition;
  }

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean visible = content.length == 1;
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation().putClientProperty(SELECTED_PROPERTY, isToFocus(context, content));
    }
  }

  private boolean isToFocus(final ViewContext context, final Content[] content) {
    return context.getRunnerLayoutUi().getOptions().isToFocus(content[0], myCondition);
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    final boolean toFocus = isToFocus(context, content);
    context.getRunnerLayoutUi().getOptions().setToFocus(toFocus ? null : content[0], myCondition);
  }
}