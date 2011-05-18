package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;

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
    if (KeymapUtil.isEmacsKeymap()) {
      // Emacs users are accustomed to the editor that executes 'find next' on subsequent pressing of shortcut that
      // activates 'incremental search'. Hence, we do the similar hack here for them.
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
      action.update(e);
      action.actionPerformed(e);
      return;
    }
    
    final FindModel findModel = getEditorSearchComponent().getFindModel();
    FindUtil.configureFindModel(false, null, findModel, false);
  }
}
