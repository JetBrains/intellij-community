package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnOutOfPermGenMemoryErrorAction extends AnAction {
  public DropAnOutOfPermGenMemoryErrorAction() {
    super ("Drop an perm gen OutOfMemoryError");
  }

  public void actionPerformed(AnActionEvent e) {
    throw new OutOfMemoryError("foo PermGen foo");
  }
}