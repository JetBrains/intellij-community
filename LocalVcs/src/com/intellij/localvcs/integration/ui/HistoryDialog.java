package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Content;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.SimpleContent;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

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

  private JComponent createLabelsTable() {
    HistoryDialog.MyTableModel m = new HistoryDialog.MyTableModel();

    JTable t = new JTable(m);

    final ListSelectionModel selectionModel = t.getSelectionModel();
    selectionModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        // todo do always selection
        int first = selectionModel.getMinSelectionIndex();
        int last = selectionModel.getMaxSelectionIndex();
        myModel.selectLabels(first, last);
        updateDiffs();
      }
    });

    return new JScrollPane(t);
  }

  protected abstract void updateDiffs();

  protected SimpleDiffRequest createDiffRequest(Content left, Content right) {
    // todo add timestamps
    SimpleDiffRequest r = new SimpleDiffRequest(null, "title");
    // todo review byte conversion
    r.setContents(new SimpleContent(new String(left.getBytes())),
                  new SimpleContent(new String(right.getBytes())));
    return r;
  }

  private class MyTableModel extends AbstractTableModel {
    public int getRowCount() {
      return myModel.getLabels().size();
    }

    public int getColumnCount() {
      return 1;
    }

    public Object getValueAt(int row, int column) {
      return myModel.getLabels().get(row);
    }
  }
}
