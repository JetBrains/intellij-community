package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class CloseViewAction extends BaseViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(context, content, e.getPlace()));
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getContentManager().removeContent(content[0], context.isToDisposeRemovedContent());
  }

  public static boolean isEnabled(ViewContext context, Content[] content, String place) {
    return content.length == 1 && content[0].isCloseable();
  }
  
}