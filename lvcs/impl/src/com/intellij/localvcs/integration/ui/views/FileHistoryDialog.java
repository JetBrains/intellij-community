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
import java.awt.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private static final String DIFF_CARD = "DIFF_CARD";
  private static final String MESSAGE_CARD = "MESSAGE_CARD";

  private DiffPanel myDiffPanel;
  private JLabel myCanNotShowDifferenceLabel;
  private JPanel myPanel;

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
    String message = "The difference cannot be shown<br>because one of the selected revisions has very long file content";
    myCanNotShowDifferenceLabel =
      new JLabel("<HTML><CENTER><B><FONT color='red'>" + message + "</FONT></B></CENTER></HTML>", JLabel.CENTER);

    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myGateway.getProject());
    DiffPanelOptions o = ((DiffPanelEx)myDiffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);

    CardLayout l = new CardLayout();
    myPanel = new JPanel(l);

    myPanel.add(myDiffPanel.getComponent(), DIFF_CARD);
    myPanel.add(myCanNotShowDifferenceLabel, MESSAGE_CARD);

    updateDiffs();

    return myPanel;
  }

  @Override
  protected void updateDiffs() {
    boolean cachedCanShowDiff = myModel.canShowDifference();
    if (cachedCanShowDiff) {
      myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
    }

    String card = cachedCanShowDiff ? DIFF_CARD : MESSAGE_CARD;
    ((CardLayout)myPanel.getLayout()).show(myPanel, card);
  }
}
