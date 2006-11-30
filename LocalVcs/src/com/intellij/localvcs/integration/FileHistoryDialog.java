package com.intellij.localvcs.integration;

import com.intellij.localvcs.Label;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class FileHistoryDialog extends DialogWrapper {
  private Project myProject;
  private VirtualFile myFile;
  private LocalVcsComponent myVcs;
  private DiffPanel myDiffPanel;

  protected FileHistoryDialog(VirtualFile f, Project p) {
    super(p, true);
    myProject = p;
    myFile = f;
    myVcs = myProject.getComponent(LocalVcsComponent.class);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final MyTableModel m = new MyTableModel();
    JTable t = new JTable(m);
    t.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myDiffPanel.setContents(m.getContentAt(e.getFirstIndex()), getCurrentFileContent());
      }
    });

    Splitter s = new Splitter(true);
    s.setFirstComponent(createDiffPanel());
    s.setSecondComponent(t);
    s.setProportion(0.7f);
    s.setPreferredSize(new Dimension(700, 600));
    return s;
  }

  private JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);
    myDiffPanel.setContents(getCurrentFileContent(), getCurrentFileContent());
    return myDiffPanel.getComponent();
  }

  private DiffContent getCurrentFileContent() {
    try {
      return new SimpleContent(new String(myFile.contentsToByteArray()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private class MyTableModel extends AbstractTableModel {
    private List<Label> myLabels;

    public MyTableModel() {
      myLabels = myVcs.getLocalVcs().getLabelsFor(myFile.getPath());
    }

    public int getRowCount() {
      return myLabels.size();
    }

    public int getColumnCount() {
      return 1;
    }

    public Object getValueAt(int row, int column) {
      return myLabels.get(row).getName();
    }

    public DiffContent getContentAt(int row) {
      return new SimpleContent(myLabels.get(row).getEntry().getContent());
    }
  }
}
