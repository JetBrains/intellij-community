package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.ide.actionMacro.EditMacrosDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 3:33:04 PM
 * To change this template use Options | File Templates.
 */
public class EditMacrosAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    EditMacrosDialog dialog = new EditMacrosDialog(DataKeys.PROJECT.getData(e.getDataContext()));
    dialog.show();
  }

  public void update(AnActionEvent e) {
    final ActionMacroManager manager = ActionMacroManager.getInstance();
    ActionMacro[] macros = manager.getAllMacros();
    e.getPresentation().setEnabled(macros != null && macros.length > 0);
  }
}
