package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.localvcs.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private CheckinPanelTreeTable myDiffTree;

  public DirectoryHistoryDialog(VirtualFile f, IdeaGateway gw) {
    super(f, gw);
    setTitle(myModel.getTitle());
  }

  @Override
  protected DirectoryHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new DirectoryHistoryDialogModel(f, vcs, myIdeaGateway);
  }

  @Override
  protected JComponent createDiffPanel() {
    initDiffTree();

    JPanel p = new JPanel(new BorderLayout());

    p.add(new DiffStatusBar(DiffStatusBar.DEFAULT_TYPES), BorderLayout.SOUTH);
    p.add(ScrollPaneFactory.createScrollPane(myDiffTree), BorderLayout.CENTER);

    ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createActionGroup(), true);
    p.add(tb.getComponent(), BorderLayout.NORTH);

    return p;
  }

  private void initDiffTree() {
    DirectoryDifferenceModel m = myModel.getRootDifferenceNodeModel();
    DirectoryDifferenceNode n = new DirectoryDifferenceNode(m);

    myDiffTree = CheckinPanelTreeTable.createOn(myIdeaGateway.getProject(), n);

    myDiffTree.getFirstTreeColumn().setName(n.getPresentableText(0));
    myDiffTree.getSecondTreeColumn().setName(n.getPresentableText(1));

    TreeUtil.expandAll(myDiffTree.getTree());
    addPopupMenuToComponent(myDiffTree, createActionGroup());
    new ShowDifferenceAction().registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, myDiffTree);
  }

  private ActionGroup createActionGroup() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new ShowDifferenceAction());
    result.add(new RevertAction());
    return result;
  }

  @Override
  protected void updateDiffs() {
    mySplitter.setFirstComponent(createDiffPanel());
    mySplitter.revalidate();
  }

  private DirectoryDifferenceNode getSelectedNode() {
    if (myDiffTree.getTree().isSelectionEmpty()) return null;
    TreePath path = myDiffTree.getTree().getSelectionPaths()[0];
    return (DirectoryDifferenceNode)path.getLastPathComponent();
  }

  private class ShowDifferenceAction extends AnAction {
    public ShowDifferenceAction() {
      super("Show difference");
    }

    public void actionPerformed(AnActionEvent e) {
      DirectoryDifferenceNode n = getSelectedNode();
      DiffRequest r = createDifference(n.getFileDifferenceModel());
      DiffManager.getInstance().getDiffTool().show(r);
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(IconLoader.getIcon("/actions/diff.png"));
      DirectoryDifferenceNode n = getSelectedNode();
      p.setEnabled(n != null && n.canShowFileDifference());
    }
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super("Revert");
    }

    public void actionPerformed(AnActionEvent e) {
      DirectoryDifferenceNode n = getSelectedNode();
      if (!myModel.revert(n.getModel())) return;
      close(0);
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(IconLoader.getIcon("/actions/rollback.png"));
    }
  }
}
