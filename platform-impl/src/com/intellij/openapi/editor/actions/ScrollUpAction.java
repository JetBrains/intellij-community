/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 10:55:09 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class ScrollUpAction extends InactiveEditorAction {
  public ScrollUpAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      EditorActionUtil.scrollRelatively(editor, -1);
    }
  }
}
