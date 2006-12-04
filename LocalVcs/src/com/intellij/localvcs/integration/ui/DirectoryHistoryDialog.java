package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.ui.impl.checkinProjectPanel.CheckinPanelTreeTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreeNode;
import java.awt.*;

public class DirectoryHistoryDialog extends DialogWrapper {
  private Project myProject;
  private CheckinPanelTreeTable myDiffTable;
  private DirectoryHistoryDialogModel myModel;
  private Splitter mySplitter;

  protected DirectoryHistoryDialog(VirtualFile f, Project p) {
    super(p, true);
    myProject = p;
    initModel(f);
    init();
  }

  private void initModel(VirtualFile f) {
    LocalVcs vcs = LocalVcsComponent.getInstance(myProject).getLocalVcs();
    myModel = new DirectoryHistoryDialogModel(f, vcs);
  }

  @Nullable
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

  private JComponent createDiffPanel() {
    TreeNode root = new DifferenceNode(myModel.getDifference());

    myDiffTable = CheckinPanelTreeTable.createOn(myProject, root, new ColumnInfo[0], new ColumnInfo[0], new AnAction[0], new AnAction[0]);
    myDiffTable.setRootVisible(true);

    return myDiffTable;
  }

  private JComponent createLabelsTable() {
    MyTableModel m = new MyTableModel();

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
    return t;
  }

  private void updateDiffs() {
    mySplitter.setFirstComponent(createDiffPanel());
    mySplitter.revalidate();
    mySplitter.repaint();
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
