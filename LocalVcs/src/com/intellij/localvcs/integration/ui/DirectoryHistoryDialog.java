package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Content;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;

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
    addPopupMenuToDiffPanel();
    return myDiffPanel;
  }

  private void addPopupMenuToDiffPanel() {
    myDiffPanel.addMouseListener(new PopupHandler() {
      public void invokePopup(Component c, int x, int y) {
        ActionPopupMenu m = createPopupMenu();
        m.getComponent().show(c, x, y);
      }
    });
  }

  private ActionPopupMenu createPopupMenu() {
    ActionGroup g = createActionGroup();
    ActionManager m = ActionManager.getInstance();
    return m.createActionPopupMenu(ActionPlaces.UNKNOWN, g);
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
