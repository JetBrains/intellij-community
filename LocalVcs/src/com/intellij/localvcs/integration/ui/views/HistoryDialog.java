package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.integration.FormatUtil;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.localvcs.integration.ui.models.HistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIHelper;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public abstract class HistoryDialog<T extends HistoryDialogModel> extends DialogWrapper {
  protected IdeaGateway myIdeaGateway;
  protected Splitter mySplitter;
  protected T myModel;

  protected HistoryDialog(VirtualFile f, IdeaGateway gw) {
    super(gw.getProject(), true);
    myIdeaGateway = gw;
    initModel(f);
    init();
  }

  private void initModel(VirtualFile f) {
    ILocalVcs vcs = LocalVcsComponent.getLocalVcsFor(myIdeaGateway.getProject());
    myModel = createModelFor(f, vcs);
  }

  protected abstract T createModelFor(VirtualFile f, ILocalVcs vcs);

  @Override
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent labels = createLabelsList();

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(diff);
    mySplitter.setSecondComponent(labels);

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

  private JComponent createLabelsList() {
    ActionGroup actions = createLabelsActions();

    ActionToolbar tb = createLabelsToolbar(actions);
    JComponent t = createLabelsTable(actions);

    JPanel result = new JPanel(new BorderLayout());
    result.add(tb.getComponent(), BorderLayout.NORTH);
    result.add(t, BorderLayout.CENTER);

    return result;
  }

  private ActionGroup createLabelsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    return result;
  }

  private ActionToolbar createLabelsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
  }

  private JComponent createLabelsTable(ActionGroup actions) {
    JTable t = new Table();
    t.setModel(new LabelsTableModel());

    addPopupMenuToComponent(t, actions);
    addSelectionListener(t);

    t.getColumnModel().getColumn(0).setMinWidth(150);
    t.getColumnModel().getColumn(0).setMaxWidth(150);

    t.getColumnModel().getColumn(0).setResizable(false);
    t.getColumnModel().getColumn(1).setResizable(false);


    return ScrollPaneFactory.createScrollPane(t);
  }

  private void addSelectionListener(JTable t) {
    final ListSelectionModel selectionModel = t.getSelectionModel();
    selectionModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int first = selectionModel.getMinSelectionIndex();
        int last = selectionModel.getMaxSelectionIndex();
        myModel.selectLabels(first, last);
        updateDiffs();
      }
    });
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
    FileTypeManager tm = FileTypeManager.getInstance();
    EditorFactory ef = EditorFactory.getInstance();

    SimpleDiffRequest r = new SimpleDiffRequest(myIdeaGateway.getProject(), m.getTitle());
    r.setContents(m.getLeftDiffContent(tm, ef), m.getRightDiffContent(tm, ef));
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

  private class LabelsTableModel extends AbstractTableModel {
    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myModel.getLabels().size();
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) return "Date";
      if (column == 1) return "Label";
      return null;
    }

    public Object getValueAt(int row, int column) {
      if (column == 0) {
        return FormatUtil.formatTimestamp(getLabelFor(row).getTimestamp());
      }
      return getLabelFor(row).getName();
    }

    private Label getLabelFor(int row) {
      return myModel.getLabels().get(row);
    }
  }

  private class RevertAction extends AnAction {
    public RevertAction() {
      super("Revert");
    }

    public void actionPerformed(AnActionEvent e) {
      if (!myModel.revert()) return;
      close(0);
    }

    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(IconLoader.getIcon("/actions/rollback.png"));
      p.setEnabled(myModel.canRevert());
    }
  }
}
