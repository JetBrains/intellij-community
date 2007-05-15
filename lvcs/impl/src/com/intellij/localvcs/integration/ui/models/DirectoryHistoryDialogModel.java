package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.revert.DirectoryReverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs, gw);
  }

  public String getTitle() {
    return myFile.getPath();
  }

  public DirectoryDifferenceModel getRootDifferenceNodeModel() {
    Difference d = getLeftRevision().getDifferenceWith(getRightRevision());
    return new DirectoryDifferenceModel(d);
  }

  @Override
  public List<String> revert() throws IOException {
    return doRevert(getRightEntry(), getLeftEntry());
  }

  public List<String> revert(DirectoryDifferenceModel m) throws IOException {
    return doRevert(m.getEntry(1), m.getEntry(0));
  }

  private List<String> doRevert(Entry rightEntry, Entry leftEntry) throws IOException {
    DirectoryReverter.revert(myVcs, myGateway, getLeftRevision(), leftEntry, rightEntry);
    return Collections.emptyList();
  }
}
