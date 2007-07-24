package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;

import java.io.IOException;
import java.util.List;

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
  protected void doCheckCanRevert(List<String> errors) throws IOException {
    super.doCheckCanRevert(errors);
    if (myLeftEntry != null && myLeftEntry.hasUnavailableContent()) {
      errors.add("some of the files have big content");
    }
  }

  @Override
  protected boolean askForReadOnlyStatusClearing() throws IOException {
    if (!hasCurrentVersion()) return true;
    return super.askForReadOnlyStatusClearing();
  }

  @Override
  protected String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myLeftRevision.getTimestamp());
  }

  protected boolean hasPreviousVersion() {
    return myLeftEntry != null;
  }

  protected boolean hasCurrentVersion() {
    if (myRightEntry == null) return false;
    return myGateway.findVirtualFile(myRightEntry.getPath()) != null;
  }
}
