package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import static javax.swing.SpringLayout.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;
  private JLabel myCanNotShowDifferenceLabel;

  public FileHistoryDialog(VirtualFile f, IdeaGateway gw) {
    super(gw, f);
  }

  @Override
  public void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new FileHistoryDialogModel(f, vcs, myGateway);
  }

  @Override
  protected JComponent createDiffPanel() {
    myCanNotShowDifferenceLabel = new JLabel(
      "<HTML><CENTER><B><FONT color='red'>The difference cannot be shown<br>because one of the selected revisions has very long file content</FONT></B></CENTER></HTML>",
      JLabel.CENTER);

    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myGateway.getProject());
    DiffPanelOptions o = ((DiffPanelEx)myDiffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);

    SpringLayout l = new SpringLayout();
    JPanel panel = new JPanel(l);

    panel.add(myDiffPanel.getComponent());
    panel.add(myCanNotShowDifferenceLabel);

    align(myCanNotShowDifferenceLabel, panel, l);
    align(myDiffPanel.getComponent(), panel, l);

    updateDiffs();

    return panel;
  }

  private void align(JComponent c, JComponent to, SpringLayout l) {
    l.putConstraint(WEST, c, 0, WEST, to);
    l.putConstraint(EAST, c, 0, EAST, to);
    l.putConstraint(NORTH, c, 0, NORTH, to);
    l.putConstraint(SOUTH, c, 0, SOUTH, to);
  }

  @Override
  protected void updateDiffs() {
    boolean canShowDifference = myModel.canShowDifference();
    if (canShowDifference) {
      myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
    }
    myDiffPanel.getComponent().setVisible(canShowDifference);
    myCanNotShowDifferenceLabel.setVisible(!canShowDifference);
  }
}
