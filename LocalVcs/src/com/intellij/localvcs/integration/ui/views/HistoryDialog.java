package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.localvcs.integration.ui.models.HistoryDialogModel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DateFormat;
import java.util.Date;

// todo it seems that this class spreads memory leaks
public abstract class HistoryDialog<T extends HistoryDialogModel> extends DialogWrapper {
  protected Project myProject;
  protected Splitter mySplitter;
  protected T myModel;

  protected HistoryDialog(VirtualFile f, Project p) {
    super(p, true);
    myProject = p;
    initModel(f);
    init();
  }

  private void initModel(VirtualFile f) {
    ILocalVcs vcs = LocalVcsComponent.getLocalVcsFor(myProject);
    myModel = createModelFor(f, vcs);
  }

  protected abstract T createModelFor(VirtualFile f, ILocalVcs vcs);

  @Override
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent labels = createLabelsTable();

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(diff);
    mySplitter.setSecondComponent(labels);
    mySplitter.setProportion(0.7f);
    mySplitter.setPreferredSize(new Dimension(700, 600));

    return mySplitter;
  }

  protected abstract JComponent createDiffPanel();

  protected JComponent createLabelsTable() {
    JTable t = new JTable(new LabelsTableModel());
    addSelectionListener(t);
    t.getColumnModel().getColumn(0).setMinWidth(150);
    t.getColumnModel().getColumn(0).setMaxWidth(150);

    t.getColumnModel().getColumn(0).setResizable(false);
    t.getColumnModel().getColumn(1).setResizable(false);
    return new JScrollPane(t);
  }

  private void addSelectionListener(JTable t) {
    final ListSelectionModel selectionModel = t.getSelectionModel();
    selectionModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        // todo do always-selected selection
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

  @Override
  protected Action[] createActions() {
    return new Action[0];
  }

  protected abstract void updateDiffs();

  protected SimpleDiffRequest createDifference(FileDifferenceModel m) {
    FileTypeManager tm = FileTypeManager.getInstance();
    EditorFactory ef = EditorFactory.getInstance();

    SimpleDiffRequest r = new SimpleDiffRequest(myProject, m.getTitle());
    r.setContents(m.getLeftDiffContent(tm, ef), m.getRightDiffContent(tm, ef));
    r.setContentTitles(m.getLeftTitle(), m.getRightTitle());

    return r;
  }

  private class LabelsTableModel extends AbstractTableModel {
    //todo uncomment after transition to jdk6: @Override
    public int getColumnCount() {
      return 2;
    }

    //todo uncomment after transition to jdk6: @Override
    public int getRowCount() {
      return myModel.getLabels().size();
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) return "Date";
      if (column == 1) return "Label";
      return null;
    }

    //todo uncomment after transition to jdk6: @Override
    public Object getValueAt(int row, int column) {
      if (column == 0) {
        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        return f.format(new Date(getLabelFor(row).getTimestamp()));
      }
      if (column == 1) return getLabelFor(row).getName();
      return null;
    }

    private Label getLabelFor(int row) {
      return myModel.getLabels().get(row);
    }
  }
}
