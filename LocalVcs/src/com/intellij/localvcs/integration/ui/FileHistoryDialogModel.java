package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.FileReverter;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class FileHistoryDialogModel extends HistoryDialogModel {
  private IdeaGateway myIdeaGateway;

  public FileHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs);
    myIdeaGateway = gw;
  }

  @Override
  protected void addNotSavedVersionTo(List<Label> l) {
    if (hasModifiedUnsavedContent()) return;
    l.add(new NotSavedLabel());
  }

  private boolean hasModifiedUnsavedContent() {
    byte[] current = stantartizeLineSeparators(getCurrentContent());
    byte[] vcs = stantartizeLineSeparators(getVcsContent());
    return Arrays.equals(current, vcs);
  }

  private byte[] stantartizeLineSeparators(Content current) {
    String s = new String(current.getBytes());
    return StringUtil.convertLineSeparators(s).getBytes();
  }

  private Content getCurrentContent() {
    byte[] b = myIdeaGateway.getDocumentByteContent(myFile);
    return new ByteContent(b);
  }

  private Content getVcsContent() {
    return getVcsEntry().getContent();
  }

  private Entry getVcsEntry() {
    return myVcs.getEntry(myFile.getPath());
  }

  public Content getLeftContent() {
    return getLeftLabel().getEntry().getContent();
  }

  public Content getRightContent() {
    return getRightLabel().getEntry().getContent();
  }

  public void revert() {
    myIdeaGateway.runWriteAction(new Callable() {
      public Object call() throws Exception {
        FileReverter.revert(myFile, getLeftEntry());
        return null;
      }
    });
  }

  private class NotSavedLabel extends Label {
    public NotSavedLabel() {
      super(null, null, null, null);
    }

    @Override
    public String getName() {
      return "not saved";
    }

    @Override
    public long getTimestamp() {
      throw new RuntimeException("not yet implemented");
    }

    @Override
    public Entry getEntry() {
      // todo what about timestamp?
      // todo review content stuff

      // todo test copying
      // todo it seems ugly
      Entry e = getVcsEntry().copy();
      e.changeContent(getCurrentContent(), null);
      return e;
    }
  }
}
