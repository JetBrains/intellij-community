package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnOutOfMemoryErrorAction extends AnAction {
  public DropAnOutOfMemoryErrorAction() {
    super ("Drop an OutOfMemoryError");
  }

  public void actionPerformed(AnActionEvent e) {
    throw new OutOfMemoryError();
  }
}