package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;

  public FileHistoryDialog(VirtualFile f, IdeaGateway gw) {
    super(f, gw);
  }

  @Override
  public void dispose() {
    myDiffPanel.dispose();
    super.dispose();
  }

  @Override
  protected FileHistoryDialogModel createModelFor(VirtualFile f, ILocalVcs vcs) {
    return new FileHistoryDialogModel(f, vcs, myIdeaGateway);
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myIdeaGateway.getProject());
    updateDiffs();
    return myDiffPanel.getComponent();
  }

  @Override
  protected void updateDiffs() {
    myDiffPanel.setDiffRequest(createDifference(myModel.getDifferenceModel()));
  }
}
