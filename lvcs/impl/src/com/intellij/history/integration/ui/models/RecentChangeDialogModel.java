package com.intellij.history.integration.ui.models;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;

import java.util.ArrayList;
import java.util.List;

public class RecentChangeDialogModel extends DirectoryHistoryDialogModel {
  private RecentChange myChange;

  public RecentChangeDialogModel(IdeaGateway gw, ILocalVcs vcs, RecentChange c) {
    super(gw, vcs, null);
    myChange = c;
    resetSelection();
  }

  @Override
  protected List<Revision> getRevisionsCache() {
    final List<Revision> result = new ArrayList<Revision>();
    result.add(myChange.getRevisionAfter());
    result.add(myChange.getRevisionBefore());
    return result;
  }

  @Override
  public String getTitle() {
    return myChange.getChangeName();
  }

  @Override
  protected void resetSelection() {
    selectChanges(0, 0);
  }
}
