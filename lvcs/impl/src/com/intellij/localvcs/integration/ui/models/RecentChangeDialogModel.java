package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.RecentChange;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.IdeaGateway;

import java.util.ArrayList;
import java.util.List;

public class RecentChangeDialogModel extends DirectoryHistoryDialogModel {
  private RecentChange myChange;

  public RecentChangeDialogModel(ILocalVcs vcs, IdeaGateway gw, RecentChange c) {
    super(null, vcs, gw);
    myChange = c;
    selectChanges(0, 0);
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
}
