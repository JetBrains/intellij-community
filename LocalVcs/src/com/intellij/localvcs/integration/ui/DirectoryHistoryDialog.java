package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Content;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private CheckinPanelTreeTable myDiffPanel;

  public DirectoryHistoryDialog(VirtualFile f, Project p) {
    super(f, p);
  }

  @Override
  protected DirectoryHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new DirectoryHistoryDialogModel(f, vcs);
  }

  @Override
  protected JComponent createDiffPanel() {
    TreeNode root = new DifferenceNode(myModel.getDifference());
    myDiffPanel = CheckinPanelTreeTable.createOn(myProject, root);
    addPopupMenuToComponent(myDiffPanel, createActionGroup());
    return myDiffPanel;
  }

  private ActionGroup createActionGroup() {
    // todo make it right
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new AnAction("show diff") {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    });
    return result;
  }

  private void showDiff() {
    DifferenceNode n = getSelectedNode();
    Content left = n.getLeftContent();
    Content right = n.getRightContent();

    SimpleDiffRequest r = createDiffRequest(left, right);
    DiffManager.getInstance().getDiffTool().show(r);
  }

  private DifferenceNode getSelectedNode() {
    // todo it's buggy
    TreePath path = myDiffPanel.getTree().getSelectionPaths()[0];
    return (DifferenceNode)path.getLastPathComponent();
  }

  @Override
  protected void updateDiffs() {
    mySplitter.setFirstComponent(createDiffPanel());
    mySplitter.revalidate();
  }
}
