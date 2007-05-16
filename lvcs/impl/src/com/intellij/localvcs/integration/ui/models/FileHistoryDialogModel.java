package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.revert.FileReverter;
import com.intellij.localvcs.integration.revert.Reverter;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;

public class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs, gw);
  }

  public boolean canShowDifference() {
    if (getLeftEntry().hasUnavailableContent()) return false;
    if (getRightEntry().hasUnavailableContent()) return false;
    return true;
  }

  public FileDifferenceModel getDifferenceModel() {
    return new FileDifferenceModel(getLeftEntry(), getRightEntry()) {
      @Override
      public String getRightTitle() {
        if (isCurrentRevisionSelected()) return "Current";
        return super.getRightTitle();
      }

      @Override
      public DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef) {
        if (isCurrentRevisionSelected()) {
          Document d = gw.getDocumentFor(myFile);
          return DocumentContent.fromDocument(gw.getProject(), d);
        }
        return super.getRightDiffContent(gw, ef);
      }
    };
  }

  @Override
  public Reverter createReverter() {
    return new FileReverter(myGateway, getLeftRevision(), getLeftEntry(), getRightEntry());
  }

  @Override
  public boolean isRevertEnabled() {
    return super.isRevertEnabled() && !getLeftEntry().hasUnavailableContent();
  }
}
