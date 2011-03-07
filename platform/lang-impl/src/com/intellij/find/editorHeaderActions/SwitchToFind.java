package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:57
* To change this template use File | Settings | File Templates.
*/
public class SwitchToFind extends EditorHeaderAction {
  public SwitchToFind(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    AnAction findAction = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND);
    if (findAction != null) {
      registerCustomShortcutSet(findAction.getShortcutSet(), editorSearchComponent);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getEditorSearchComponent().getFindModel().setReplaceState(false);
  }
}
