package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class FileHistoryDialog extends DialogWrapper {
  private Project myProject;
  private DiffPanel myDiffPanel;
  private FileHistoryDialogModel myModel;

  protected FileHistoryDialog(VirtualFile f, Project p) {
    super(p, true);
    myProject = p;
    initModel(f);
    init();
  }

  private void initModel(VirtualFile f) {
    LocalVcs vcs = LocalVcsComponent.getInstance(myProject).getLocalVcs();
    FileDocumentManager dm = FileDocumentManager.getInstance();
    myModel = new FileHistoryDialogModel(f, vcs, dm);
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JComponent diff = createDiffPanel();
    JComponent labels = createLabelsTable();

    Splitter s = new Splitter(true);
    s.setFirstComponent(diff);
    s.setSecondComponent(labels);
    s.setProportion(0.7f);
    s.setPreferredSize(new Dimension(700, 600));

    return s;
  }

  private JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);
    updateDiffs();
    return myDiffPanel.getComponent();
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
    SimpleContent left = new SimpleContent(myModel.getLeftContent());
    SimpleContent right = new SimpleContent(myModel.getRightContent());

    myDiffPanel.setContents(left, right);
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
