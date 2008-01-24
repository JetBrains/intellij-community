package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.TreeAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;

public class TreeActionWrapper extends ToggleAction{
  private final TreeAction myAction;
  private final TreeActionsOwner myStructureView;


  public TreeActionWrapper(TreeAction action, TreeActionsOwner structureView) {
    myAction = action;
    myStructureView = structureView;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    ActionPresentation actionPresentation = myAction.getPresentation();
    presentation.setIcon(actionPresentation.getIcon());
    presentation.setText(actionPresentation.getText());
  }

  public boolean isSelected(AnActionEvent e) {
    return TreeModelWrapper.isActive(myAction, myStructureView);
  }

  public void setSelected(AnActionEvent e, boolean state) {
    myStructureView.setActionActive(myAction.getName(), TreeModelWrapper.shouldRevert(myAction) ?  !state : state);
  }
}
