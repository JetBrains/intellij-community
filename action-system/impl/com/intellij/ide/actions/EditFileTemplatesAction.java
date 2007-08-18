package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;

public class EditFileTemplatesAction extends AnAction{
  public EditFileTemplatesAction(String text) {
    super(text);
  }

  public void actionPerformed(AnActionEvent e){
    ConfigureTemplatesDialog dialog = new ConfigureTemplatesDialog(DataKeys.PROJECT.getData(e.getDataContext()));
    dialog.show();
  }
}
