package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.ui.content.Content;

public class AttachCellAction extends BaseDebuggerViewAction {

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length == 0 || !isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.findCellFor(content[0]).attach();
  }
}
