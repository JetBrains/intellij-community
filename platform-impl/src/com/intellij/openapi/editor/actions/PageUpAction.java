/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 3:16:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.actionSystem.DataContext;

public class PageUpAction extends EditorAction {
  public static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      EditorActionUtil.moveCaretPageUp(editor, false);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isOneLineMode();
    }
  }

  public PageUpAction() {
    super(new Handler());
    
  }
}
