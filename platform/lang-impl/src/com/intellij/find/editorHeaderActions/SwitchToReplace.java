package com.intellij.find.editorHeaderActions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:57
* To change this template use File | Settings | File Templates.
*/
public class SwitchToReplace extends EditorHeaderAction {
  public SwitchToReplace(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    AnAction replaceAction = ActionManager.getInstance().getAction("Replace");
    if (replaceAction != null) {
      registerCustomShortcutSet(replaceAction.getShortcutSet(), editorSearchComponent);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Editor editor = getEditorSearchComponent().getEditor();
    e.getPresentation().setEnabled(!ConsoleViewUtil.isConsoleViewEditor(editor));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FindModel findModel = getEditorSearchComponent().getFindModel();
    FindUtil.configureFindModel(true, null, findModel, false);
  }
}
