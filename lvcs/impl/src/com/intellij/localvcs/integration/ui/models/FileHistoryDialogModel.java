package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.Clock;
import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.storage.ByteContent;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.FileEntry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.List;

public class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs, gw);
  }

  @Override
  protected void addNotSavedVersionTo(List<Revision> rr) {
    if (hasModifiedUnsavedContent()) return;
    rr.add(new NotSavedRevision());
  }

  private boolean hasModifiedUnsavedContent() {
    byte[] current = stantardizeLineSeparators(getCurrentContent());
    byte[] vcs = stantardizeLineSeparators(getVcsContent());
    return Arrays.equals(current, vcs);
  }

  private byte[] stantardizeLineSeparators(Content current) {
    String s = new String(current.getBytes());
    return StringUtil.convertLineSeparators(s).getBytes();
  }

  private Content getCurrentContent() {
    byte[] b = myGateway.getDocumentByteContent(myFile);
    return new ByteContent(b);
  }

  private Content getVcsContent() {
    return getVcsEntry().getContent();
  }

  private Entry getVcsEntry() {
    return myVcs.getEntry(myFile.getPath());
  }

  public FileDifferenceModel getDifferenceModel() {
    return new FileDifferenceModel(getLeftEntry(), getRightEntry());
  }

  private class NotSavedRevision extends Revision {
    private long myTimestamp;

    public NotSavedRevision() {
      myTimestamp = Clock.getCurrentTimestamp();
    }

    @Override
    public String getName() {
      return "not saved";
    }

    @Override
    public long getTimestamp() {
      return myTimestamp;
    }

    @Override
    public Entry getEntry() {
      // todo review content stuff
      // todo it seems ugly
      final Entry e = getVcsEntry();
      return new FileEntry(-1, e.getName(), getCurrentContent(), getTimestamp()) {
        @Override
        public String getPath() {
          return e.getPath();
        }
      };
    }
  }
}
