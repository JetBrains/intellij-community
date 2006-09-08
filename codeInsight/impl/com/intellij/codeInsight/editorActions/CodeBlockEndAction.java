/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:49:27 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

public class CodeBlockEndAction extends EditorAction {
  public CodeBlockEndAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      if (project != null) {
        CodeBlockUtil.moveCaretToCodeBlockEnd(project, editor, false);
      }
    }
  }
}
