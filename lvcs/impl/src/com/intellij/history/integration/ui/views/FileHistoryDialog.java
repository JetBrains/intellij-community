package com.intellij.history.integration.ui.views;

import static com.intellij.history.integration.LocalHistoryBundle.message;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import static com.intellij.history.integration.LocalHistoryBundle.*;
import static com.intellij.history.integration.LocalHistoryBundle.*;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
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

  public FileHistoryDialog(IdeaGateway gw, VirtualFile f) {
    this(gw, f, true);
  }

  protected FileHistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw, f, doInit);
  }

  @Override
  protected void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModel(ILocalVcs vcs) {
    return new FileHistoryDialogModel(myGateway, vcs, myFile);
  }

  @Override
  protected Dimension getInitialSize() {
    Dimension ss = Toolkit.getDefaultToolkit().getScreenSize();
    return new Dimension(ss.width - 40, ss.height - 40);
  }

  @Override
  protected JComponent createDiffPanel() {
    myCanNotShowDifferenceLabel =
      new JLabel(getFormattedCanNotShowDiffMessage(), JLabel.CENTER);

    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), getProject());
    DiffPanelOptions o = ((DiffPanelEx)myDiffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);

    CardLayout l = new CardLayout();
    myPanel = new JPanel(l);

    myPanel.add(myDiffPanel.getComponent(), DIFF_CARD);
    myPanel.add(myCanNotShowDifferenceLabel, MESSAGE_CARD);

    updateDiffs();

    return myPanel;
  }

  private String getFormattedCanNotShowDiffMessage() {
    String message = message("message.can.not.show.diffecence.because.of.big.files");
    message = message.replaceAll("\n", "<br>");
    message = "<HTML><CENTER><B><FONT color='red'>" + message + "</FONT></B></CENTER></HTML>";
    return message;
  }

  @Override
  protected void updateDiffs() {
    final boolean[] cachedCanShowDiff = new boolean[1];

    processRevisions(new RevisionProcessingTask() {
      public void run(RevisionProcessingProgress p) {
        cachedCanShowDiff[0] = myModel.canShowDifference(p);
      }
    });

    if (cachedCanShowDiff[0]) {
      myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
    }

    String card = cachedCanShowDiff[0] ? DIFF_CARD : MESSAGE_CARD;
    ((CardLayout)myPanel.getLayout()).show(myPanel, card);
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.localHistory.show.file";
  }
}
