package com.intellij.localvcs.integration.revert;

import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.FormatUtil;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.utils.RunnableAdapter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public abstract class RevisionReverter {
  protected IdeaGateway myGateway;
  protected Revision myLeftRevision;
  protected Entry myLeftEntry;
  protected Entry myRightEntry;

  public RevisionReverter(IdeaGateway gw, Revision leftRevision, Entry leftEntry, Entry rightEntry) {
    myGateway = gw;
    myLeftRevision = leftRevision;
    myLeftEntry = leftEntry;
    myRightEntry = rightEntry;
  }

  public List<String> checkCanRevert() throws IOException {
    List<String> errors = new ArrayList<String>();
    if (!askForReadOnlyStatusClearing()) {
      errors.add("some files are read-only");
    }
    if (myLeftEntry != null && myLeftEntry.hasUnavailableContent()) {
      errors.add("some of the files have big content");
    }

    doCheckCanRevert(errors);
    return removeDuplicatesAndSort(errors);
  }

  protected boolean askForReadOnlyStatusClearing() {
    if (!hasCurrentVersion()) return true;
    return myGateway.ensureFilesAreWritable(getFilesToClearROStatus());
  }

  protected abstract List<VirtualFile> getFilesToClearROStatus();

  protected abstract void doCheckCanRevert(List<String> errors) throws IOException;

  private List<String> removeDuplicatesAndSort(List<String> list) {
    List<String> result = new ArrayList<String>(new HashSet<String>(list));
    Collections.sort(result);
    return result;
  }

  public void revert() throws IOException {
    try {
      myGateway.performCommandInsideWriteAction(formatCommandName(), new RunnableAdapter() {
        @Override
        public void doRun() throws Exception {
          myGateway.saveAllUnsavedDocuments();
          doRevert();
        }
      });
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      throw e;
    }
  }

  private String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myLeftRevision.getTimestamp());
  }

  public abstract void doRevert() throws IOException;

  protected boolean hasPreviousVersion() {
    return myLeftEntry != null;
  }

  protected boolean hasCurrentVersion() {
    return myRightEntry != null;
  }
}
