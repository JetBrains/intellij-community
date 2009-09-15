package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(IdeaGateway gw, LocalVcs vcs, VirtualFile f) {
    super(gw, vcs, f);
  }

  public String getTitle() {
    return myFile.getPath();
  }

  @Override
  protected DirectoryChange createChange(Difference d) {
    return new DirectoryChange(new DirectoryChangeModel(d, myGateway, isCurrentRevisionSelected()));
  }

  @Override
  protected Reverter createRevisionReverter() {
    return createRevisionReverter(getLeftRevision().getDifferencesWith(getRightRevision()));
  }

  public Reverter createRevisionReverter(List<Difference> diffs) {
    return new DifferenceReverter(myVcs, myGateway, diffs, getLeftRevision());
  }
}
