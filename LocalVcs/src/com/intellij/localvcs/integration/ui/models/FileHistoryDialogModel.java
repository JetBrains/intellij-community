package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.*;
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
  protected void addNotSavedVersionTo(List<Label> l) {
    if (hasModifiedUnsavedContent()) return;
    l.add(new NotSavedLabel());
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

  private class NotSavedLabel extends Label {
    public NotSavedLabel() {
      super(null, null, null, null, "not saved", Clock.getCurrentTimestamp());
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
