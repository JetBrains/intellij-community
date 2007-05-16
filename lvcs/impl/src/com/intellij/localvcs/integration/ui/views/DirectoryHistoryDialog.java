package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
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

  public DirectoryHistoryDialog(IdeaGateway gw, VirtualFile f) {
    super(gw, f);
    setTitle(myModel.getTitle());
  }

  @Override
  protected DirectoryHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new DirectoryHistoryDialogModel(f, vcs, myGateway);
  }

  @Override
  protected JComponent createDiffPanel() {
    initDiffTree();

    JPanel p = new JPanel(new BorderLayout());

    p.add(new DiffStatusBar(DiffStatusBar.DEFAULT_TYPES), BorderLayout.SOUTH);
    p.add(ScrollPaneFactory.createScrollPane(myDiffTree), BorderLayout.CENTER);

    ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createDiffTreeActions(), true);
    p.add(tb.getComponent(), BorderLayout.NORTH);

    return p;
  }

  private void initDiffTree() {
    DirectoryDifferenceModel m = myModel.getRootDifferenceNodeModel();
    DirectoryDifferenceNode n = new DirectoryDifferenceNode(m);

    myDiffTree = CheckinPanelTreeTable.createOn(myGateway.getProject(), n);

    myDiffTree.getFirstTreeColumn().setName(n.getPresentableText(0));
    myDiffTree.getSecondTreeColumn().setName(n.getPresentableText(1));

    TreeUtil.expandAll(myDiffTree.getTree());
    addPopupMenuToComponent(myDiffTree, createDiffTreeActions());
    new ShowDifferenceAction().registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, myDiffTree);
  }

  private ActionGroup createDiffTreeActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new ShowDifferenceAction());
    result.add(new RevertSelectionAction());
    return result;
  }

  @Override
  protected void updateDiffs() {
    mySplitter.setFirstComponent(createDiffPanel());
    mySplitter.revalidate();
  }

  private DirectoryDifferenceNode getSelectedNode() {
    if (myDiffTree.getTree().getSelectionCount() != 1) return null;
    TreePath path = myDiffTree.getTree().getSelectionPath();
    return (DirectoryDifferenceNode)path.getLastPathComponent();
  }

  private class ShowDifferenceAction extends ActionOnSelection {
    public ShowDifferenceAction() {
      super("Show difference", "/actions/diff.png");
    }

    @Override
    protected void performOn(DirectoryDifferenceNode n) {
      DiffRequest r = createDifference(n.getFileDifferenceModel());
      DiffManager.getInstance().getDiffTool().show(r);
    }

    @Override
    protected boolean isEnabledFor(DirectoryDifferenceNode n) {
      return n.canShowFileDifference();
    }
  }

  private class RevertSelectionAction extends ActionOnSelection {
    public RevertSelectionAction() {
      super("Revert selection", "/actions/rollback.png");
    }

    @Override
    protected void performOn(final DirectoryDifferenceNode n) {
      revert(myModel.createReverter(n.getModel()));
    }

    @Override
    protected boolean isEnabledFor(DirectoryDifferenceNode n) {
      return myModel.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends AnAction {
    private Icon myIcon;

    public ActionOnSelection(String name, String iconName) {
      super(name);
      myIcon = IconLoader.getIcon(iconName);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      performOn(getSelectedNode());
    }

    protected abstract void performOn(DirectoryDifferenceNode n);

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(myIcon);
      DirectoryDifferenceNode n = getSelectedNode();
      p.setEnabled(n != null && isEnabledFor(n));
    }

    protected boolean isEnabledFor(DirectoryDifferenceNode n) {
      return true;
    }
  }
}
