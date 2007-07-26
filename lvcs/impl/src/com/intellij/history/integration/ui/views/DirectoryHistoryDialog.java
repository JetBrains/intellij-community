package com.intellij.history.integration.ui.views;

import static com.intellij.history.integration.LocalHistoryBundle.message;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private ChangesTreeList<Change> myChangesTree;

  public DirectoryHistoryDialog(IdeaGateway gw, VirtualFile f) {
    this(gw, f, true);
  }

  protected DirectoryHistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw, f, doInit);
  }

  @Override
  protected void init() {
    super.init();
    setTitle(myModel.getTitle());
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(ILocalVcs vcs) {
    return new DirectoryHistoryDialogModel(myGateway, vcs, myFile);
  }

  @Override
  protected Dimension getInitialSize() {
    return new Dimension(700, 600);
  }

  @Override
  protected JComponent createDiffPanel() {
    initChangesTree();

    JPanel p = new JPanel(new BorderLayout());

    p.add(new DiffStatusBar(DiffStatusBar.DEFAULT_TYPES), BorderLayout.SOUTH);
    p.add(myChangesTree, BorderLayout.CENTER);

    ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createChangesTreeActions(), true);
    p.add(tb.getComponent(), BorderLayout.NORTH);

    return p;
  }

  private void initChangesTree() {
    myChangesTree = createChangesTree();
    registerChangesTreeActions();
    updateDiffs();
  }

  private ChangesTreeList<Change> createChangesTree() {
    return new ChangesTreeList<Change>(getProject(), Collections.<Change>emptyList(), false, false) {
      @Override
      protected DefaultTreeModel buildTreeModel(List<Change> cc) {
        return new TreeModelBuilder(getProject(), false).buildModel(cc);
      }

      @Override
      protected List<Change> getSelectedObjects(ChangesBrowserNode node) {
        return node.getAllChangesUnder();
      }
    };
  }

  private void registerChangesTreeActions() {
    myChangesTree.setDoubleClickHandler(new Runnable() {
      public void run() {
        ShowDifferenceAction a = new ShowDifferenceAction();
        if (a.isEnabled()) a.perform();
      }
    });
    new ShowDifferenceAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myChangesTree);
    myChangesTree.installPopupHandler(createChangesTreeActions());
  }

  private ActionGroup createChangesTreeActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new ShowDifferenceAction());
    result.add(new RevertSelectionAction());
    return result;
  }

  @Override
  protected void updateDiffs() {
    List<Change> changes = new ArrayList<Change>();
    flatternChanges(myModel.getRootDifferenceNodeModel(), changes);
    myChangesTree.setChangesToDisplay(changes);
  }

  private void flatternChanges(DirectoryDifferenceModel m, List<Change> changes) {
    if (!m.getDifferenceKind().equals(Difference.Kind.NOT_MODIFIED)) {
      changes.add(new DirectoryDifference(m));
    }
    for (DirectoryDifferenceModel child : m.getChildren()) {
      flatternChanges(child, changes);
    }
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.localHistory.show.folder";
  }

  private DirectoryDifference getSelectedChange() {
    List<Change> selected = myChangesTree.getSelectedChanges();
    if (selected.size() != 1) return null;
    return (DirectoryDifference)selected.get(0);
  }

  private class ShowDifferenceAction extends ActionOnSelection {
    public ShowDifferenceAction() {
      super(message("action.show.difference"), "/actions/diff.png");
    }

    @Override
    protected void performOn(DirectoryDifference c) {
      DiffRequest r = createDifference(c.getFileDifferenceModel());
      DiffManager.getInstance().getDiffTool().show(r);
    }

    @Override
    protected boolean isEnabledFor(DirectoryDifference c) {
      return c.canShowFileDifference();
    }
  }

  private class RevertSelectionAction extends ActionOnSelection {
    public RevertSelectionAction() {
      super(message("action.revert.selection"), "/actions/rollback.png");
    }

    @Override
    protected void performOn(DirectoryDifference c) {
      revert(myModel.createRevisionReverter(c.getModel()));
    }

    @Override
    protected boolean isEnabledFor(DirectoryDifference c) {
      return myModel.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends AnAction {
    public ActionOnSelection(String name, String iconName) {
      super(name, null, IconLoader.getIcon(iconName));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      perform();
    }

    public void perform() {
      performOn(getSelectedChange());
    }

    protected abstract void performOn(DirectoryDifference c);

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isEnabled());
    }

    public boolean isEnabled() {
      DirectoryDifference c = getSelectedChange();
      return c != null && isEnabledFor(c);
    }

    protected boolean isEnabledFor(DirectoryDifference c) {
      return true;
    }
  }
}
