package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.treetable.TreeTable;

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
  protected DirectoryHistoryDialogModel createModelFor(VirtualFile f, LocalVcs vcs) {
    return new DirectoryHistoryDialogModel(f, vcs);
  }

  @Override
  protected JComponent createDiffPanel() {
    TreeNode root = new DifferenceNode(myModel.getDifference());

    myDiffPanel = CheckinPanelTreeTable.createOn(myProject, root, new ColumnInfo[0], new ColumnInfo[0], new AnAction[0], new AnAction[0]);
    myDiffPanel.setRootVisible(true); //  todo move it to factory method;

    myDiffPanel.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionGroup actions = getActionGroup();
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, actions);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    return myDiffPanel;
  }

  private ActionGroup getActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();

    result.add(new AnAction("show diff") {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    });

    return result;
  }

  private void showDiff() {
    TreePath path = myDiffPanel.getTree().getSelectionPaths()[0];
    DifferenceNode n = (DifferenceNode)path.getLastPathComponent();

    SimpleDiffRequest diffData = new SimpleDiffRequest(myProject, "");

    String left = n.getLeftEntry().getContent();
    String right = n.getRightEntry().getContent();

    diffData.setContents(new SimpleContent(left), new SimpleContent(right));
    DiffManager.getInstance().getDiffTool().show(diffData);
  }

  @Override
  protected void updateDiffs() {
    mySplitter.setFirstComponent(createDiffPanel());
    mySplitter.revalidate();
  }
}
