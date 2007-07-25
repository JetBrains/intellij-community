package com.intellij.history.integration.ui.views;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryComponent;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.table.RevisionsTable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
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
  private RevisionsTable myRevisionsTable;
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
    ILocalVcs vcs = LocalHistoryComponent.getLocalVcsFor(getProject());
    myModel = createModel(vcs);
    restoreShowChangesOnlyOption();
  }

  protected abstract T createModel(ILocalVcs vcs);

  @Override
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent revisions = createRevisionsList();

    mySplitter = new Splitter(true, 0.7f);
    mySplitter.setFirstComponent(diff);
    mySplitter.setSecondComponent(revisions);

    mySplitter.setPreferredSize(getInitialSize());
    restoreSplitterProportion();

    return mySplitter;
  }

  protected abstract Dimension getInitialSize();

  @Override
  protected void dispose() {
    saveShowChangesOnlyOption();
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

  private ActionToolbar createRevisionsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
  }

  private ActionGroup createRevisionsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    result.add(new ShowChangesOnlyAction());
    result.add(Separator.getInstance());
    result.add(new HelpAction());
    return result;
  }

  private JComponent createRevisionsTable(ActionGroup actions) {
    myRevisionsTable = new RevisionsTable(myModel, new RevisionsTable.SelectionListener() {
      public void changesSelected(int first, int last) {
        myModel.selectChanges(first, last);
        updateDiffs();
      }

      public void revisionsSelected(int first, int last) {
        myModel.selectRevisions(first, last);
        updateDiffs();
      }
    });
    addPopupMenuToComponent(myRevisionsTable, actions);

    return ScrollPaneFactory.createScrollPane(myRevisionsTable);
  }

  private void addPopupMenuToComponent(JComponent comp, final ActionGroup ag) {
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

  protected SimpleDiffRequest createDifference(final FileDifferenceModel m) {
    final SimpleDiffRequest r = new SimpleDiffRequest(getProject(), m.getTitle());

    processRevisions(new RevisionProcessingTask() {
      public void run(RevisionProcessingProgress p) {
        EditorFactory ef = EditorFactory.getInstance();

        p.processingLeftRevision();
        DiffContent left = m.getLeftDiffContent(myGateway, ef, p);

        p.processingRightRevision();
        DiffContent right = m.getRightDiffContent(myGateway, ef, p);

        r.setContents(left, right);
        r.setContentTitles(m.getLeftTitle(), m.getRightTitle());
      }
    });

    return r;
  }

  private void restoreShowChangesOnlyOption() {
    myModel.showChangesOnly(LocalHistoryConfiguration.getInstance().SHOW_CHANGES_ONLY);
  }

  private void saveShowChangesOnlyOption() {
    boolean value = myModel.doesShowChangesOnly();
    LocalHistoryConfiguration.getInstance().SHOW_CHANGES_ONLY = value;
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

  protected abstract String getHelpId();

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
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

  protected void showHelp() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected void processRevisions(final RevisionProcessingTask t) {
    new Task.Modal(getProject(), "Processing revisions", false) {
      public void run(ProgressIndicator i) {
        t.run(new RevisionProcessingProgressAdapter(i));
      }
    }.queue();
  }

  protected Project getProject() {
    return myGateway.getProject();
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super("Revert", null, IconLoader.getIcon("/actions/rollback.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      revert();
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isRevertEnabled());
    }
  }

  private class ShowChangesOnlyAction extends ToggleAction {
    public ShowChangesOnlyAction() {
      super("Show Changes Only", null, IconLoader.getIcon("/actions/showChangesOnly.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myModel.doesShowChangesOnly();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myModel.showChangesOnly(state);
      myRevisionsTable.updateData();
    }
  }

  private class HelpAction extends AnAction {
    public HelpAction() {
      super("Help", null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      doHelpAction();
    }
  }

  protected static interface RevisionProcessingTask {
    void run(RevisionProcessingProgress p);
  }

  private static class RevisionProcessingProgressAdapter implements RevisionProcessingProgress {
    private final ProgressIndicator myIndicator;

    public RevisionProcessingProgressAdapter(ProgressIndicator i) {
      myIndicator = i;
    }

    public void processingLeftRevision() {
      myIndicator.setText("Processing left revision");
    }

    public void processingRightRevision() {
      myIndicator.setText("Processing right revision");
    }

    public void processed(int percentage) {
      myIndicator.setFraction(percentage / 100.0);
    }
  }
}
