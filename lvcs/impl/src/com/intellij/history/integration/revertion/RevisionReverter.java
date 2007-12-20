package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public abstract class RevisionReverter extends Reverter {
  protected Revision myLeftRevision;
  protected Entry myLeftEntry;
  protected Entry myRightEntry;

  public RevisionReverter(ILocalVcs vcs, IdeaGateway gw, Revision leftRevision, Entry leftEntry, Entry rightEntry) {
    super(vcs, gw);
    myLeftRevision = leftRevision;
    myLeftEntry = leftEntry;
    myRightEntry = rightEntry;
  }

  @Override
  public List<String> askUserForProceeding() throws IOException {
    if (myLeftEntry == null) return Collections.emptyList();

    List<Entry> myEntriesWithBigContent = new ArrayList<Entry>();
    if (!myLeftEntry.hasUnavailableContent(myEntriesWithBigContent)) return Collections.emptyList();

    String filesList = "";
    for (Entry e : myEntriesWithBigContent) {
      if (filesList.length() > 0) filesList += "\n";
      filesList += e.getPath();
    }

    return Collections.singletonList(LocalHistoryBundle.message("revert.message.some.files.have.unversioned.content", filesList));
  }

  @Override
  protected boolean askForReadOnlyStatusClearing() throws IOException {
    if (!hasCurrentVersion()) return true;
    return super.askForReadOnlyStatusClearing();
  }

  @Override
  protected String formatCommandName() {
    String date = FormatUtil.formatTimestamp(myLeftRevision.getTimestamp());
    return LocalHistoryBundle.message("system.label.revert.to.date", date);
  }

  protected boolean hasPreviousVersion() {
    return myLeftEntry != null;
  }

  protected boolean hasCurrentVersion() {
    if (myRightEntry == null) return false;
    return myGateway.findVirtualFile(myRightEntry.getPath()) != null;
  }
}
