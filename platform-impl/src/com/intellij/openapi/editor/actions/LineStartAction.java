/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:29:03 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class LineStartAction extends TextComponentEditorAction {
  public LineStartAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      EditorActionUtil.moveCaretToLineStart(editor, false);
    }
  }
}
