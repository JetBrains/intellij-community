/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:20:22 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.actionSystem.DataContext;

public class MoveUpAndScrollAction extends EditorAction {
  public MoveUpAndScrollAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      EditorActionUtil.moveCaretRelativelyAndScroll(editor, 0, -1, false);
    }
  }
}
