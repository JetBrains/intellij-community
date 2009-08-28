/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 9:58:23 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.actionSystem.EditorAction;

public class MoveStatementUpAction extends EditorAction {
  public MoveStatementUpAction() {
    super(new MoveStatementHandler(false));
  }
}
