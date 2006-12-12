package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Content;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffPanel myDiffPanel;

  public FileHistoryDialog(VirtualFile f, Project p) {
    super(f, p);
  }

  @Override
  protected FileHistoryDialogModel createModelFor(VirtualFile f, LocalVcs vcs) {
    FileDocumentManager dm = FileDocumentManager.getInstance();
    return new FileHistoryDialogModel(f, vcs, dm);
  }

  @Override
  protected JComponent createDiffPanel() {
    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject);
    updateDiffs();
    return myDiffPanel.getComponent();
  }

  @Override
  protected void updateDiffs() {
    Content left = myModel.getLeftContent();
    Content right = myModel.getRightContent();

    myDiffPanel.setDiffRequest(createDiffRequest(left, right));
  }
}
