package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalHistoryComponent;
import com.intellij.localvcs.integration.revertion.Reverter;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.localvcs.integration.ui.models.HistoryDialogModel;
import com.intellij.localvcs.integration.ui.views.table.RevisionsTable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public abstract class HistoryDialog<T extends HistoryDialogModel> extends DialogWrapper {
  protected IdeaGateway myGateway;
  protected VirtualFile myFile;
  protected Splitter mySplitter;
  protected T myModel;

  protected HistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw.getProject(), true);
    myGateway = gw;
    myFile = f;
    if (doInit) init();
  }

  @Override
  protected void init() {
    initModel();
    super.init();
  }

  private void initModel() {
    ILocalVcs vcs = LocalHistoryComponent.getLocalVcsFor(myGateway.getProject());
    myModel = createModel(vcs);
  }

  protected abstract T createModel(ILocalVcs vcs);

  @Override
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent revisions = createRevisionsList();

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(diff);
    mySplitter.setSecondComponent(revisions);

    mySplitter.setPreferredSize(new Dimension(700, 600));
    mySplitter.setProportion(0.7f);
    restoreSplitterProportion();

    return mySplitter;
  }

  @Override
  public void dispose() {
    saveSplitterProportion();
    super.dispose();
  }

  protected abstract JComponent createDiffPanel();

  private JComponent createRevisionsList() {
    ActionGroup actions = createRevisionsActions();

    ActionToolbar tb = createRevisionsToolbar(actions);
    JComponent t = createRevisionsTable(actions);

    JPanel result = new JPanel(new BorderLayout());
    result.add(tb.getComponent(), BorderLayout.NORTH);
    result.add(t, BorderLayout.CENTER);

    return result;
  }

  private ActionGroup createRevisionsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    return result;
  }

  private ActionToolbar createRevisionsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
  }

  private JComponent createRevisionsTable(ActionGroup actions) {
    JTable t = new RevisionsTable(myModel, new RevisionsTable.SelectionListener() {
      public void changesSelected(int first, int last) {
        myModel.selectChanges(first, last);
        updateDiffs();
      }

      public void revisionsSelected(int first, int last) {
        myModel.selectRevisions(first, last);
        updateDiffs();
      }
    });
    addPopupMenuToComponent(t, actions);

    return ScrollPaneFactory.createScrollPane(t);
  }

  protected void addPopupMenuToComponent(JComponent comp, final ActionGroup ag) {
    comp.addMouseListener(new PopupHandler() {
      public void invokePopup(Component c, int x, int y) {
        ActionPopupMenu m = createPopupMenu(ag);
        m.getComponent().show(c, x, y);
      }
    });
  }

  private ActionPopupMenu createPopupMenu(ActionGroup ag) {
    ActionManager m = ActionManager.getInstance();
    return m.createActionPopupMenu(ActionPlaces.UNKNOWN, ag);
  }

  protected abstract void updateDiffs();

  protected SimpleDiffRequest createDifference(FileDifferenceModel m) {
    EditorFactory ef = EditorFactory.getInstance();

    SimpleDiffRequest r = new SimpleDiffRequest(myGateway.getProject(), m.getTitle());

    DiffContent left = m.getLeftDiffContent(myGateway, ef);
    DiffContent right = m.getRightDiffContent(myGateway, ef);

    r.setContents(left, right);
    r.setContentTitles(m.getLeftTitle(), m.getRightTitle());

    return r;
  }

  private void restoreSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.externalizeFromDimensionService(getDimensionServiceKey());
    d.restoreSplitterProportions(mySplitter);
  }

  private void saveSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.saveSplitterProportions(mySplitter);
    d.externalizeToDimensionService(getDimensionServiceKey());
  }

  private SplitterProportionsData createSplitterData() {
    UIHelper h = PeerFactory.getInstance().getUIHelper();
    return h.createSplitterProportionsData();
  }

  @Override
  protected String getDimensionServiceKey() {
    // enables size auto-save
    return getClass().getName();
  }

  @Override
  protected Action[] createActions() {
    // removes ok/cancel buttons
    return new Action[0];
  }

  protected void revert() {
    revert(myModel.createReverter());
  }

  private boolean isRevertEnabled() {
    return myModel.isRevertEnabled();
  }

  protected void revert(Reverter r) {
    try {
      String question = r.askUserForProceed();
      if (question != null) {
        if (!myGateway.askForProceed(question + "\nDo you want to proceed?")) {
          return;
        }
      }

      List<String> errors = r.checkCanRevert();
      if (!errors.isEmpty()) {
        showRevertErrors(errors);
        return;
      }
      r.revert();
      close(0);
    }
    catch (IOException e) {
      myGateway.showError("Error reverting changes: " + e);
    }
  }

  private void showRevertErrors(List<String> errors) {
    String formatted = "";
    if (errors.size() == 1) {
      formatted += errors.get(0);
    }
    else {
      for (String e : errors) {
        formatted += "\n    -" + e;
      }
    }
    myGateway.showError("Can not revert because " + formatted);
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super("Revert");
    }

    public void actionPerformed(AnActionEvent e) {
      revert();
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(IconLoader.getIcon("/actions/rollback.png"));
      p.setEnabled(isRevertEnabled());
    }
  }
}
