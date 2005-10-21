package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.keymap.KeyMapBundle;

import javax.swing.*;

public class EditQuickListDialog extends DialogWrapper {
  private QuickList myList;
  private QuickList[] myAllQuickLists;
  private QuickListPanel myPanel;
  private Project myProject;

  public EditQuickListDialog(Project project, QuickList list, QuickList[] allQuickLists) {
    super(project, true);
    myProject = project;
    myList = list;
    myAllQuickLists = allQuickLists;
    setTitle(KeyMapBundle.message("edit.quick.list.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myPanel = new QuickListPanel(myList, myAllQuickLists, myProject);
    return myPanel.getPanel();
  }

  public QuickList getList() {
    return myList;
  }

  protected void doOKAction() {
    ListModel model = myPanel.getActionsList().getModel();
    int size = model.getSize();
    String[] ids = new String[size];
    for (int i = 0; i < size; i++) {
      String actionId = (String)model.getElementAt(i);
      ids[i] = actionId;
    }

    myList = new QuickList(myPanel.getDisplayName(), myPanel.getDescription(), ids, myList.isReadonly());

    super.doOKAction();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.keymap.impl.ui.EditQuickListDialog";
  }
}