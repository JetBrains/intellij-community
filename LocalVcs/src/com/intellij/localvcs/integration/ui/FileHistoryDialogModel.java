package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class FileHistoryDialogModel extends HistoryDialogModel {
  private FileDocumentManager myDocumentManager;

  public FileHistoryDialogModel(VirtualFile f, LocalVcs vcs, FileDocumentManager dm) {
    super(f, vcs);
    myDocumentManager = dm;
  }

  @Override
  protected void addCurrentVersionTo(List<Label> l) {
    if (getCurrentContent().equals(getVcsContent())) return;
    l.add(new CurrentLabel());
  }

  private String getCurrentContent() {
    return myDocumentManager.getDocument(myFile).getText();
  }

  private String getVcsContent() {
    return getVcsEntry().getContent();
  }

  private Entry getVcsEntry() {
    return myVcs.getEntry(myFile.getPath());
  }

  public String getLeftContent() {
    return getLeftLabel().getEntry().getContent();
  }

  public String getRightContent() {
    return getRightLabel().getEntry().getContent();
  }

  private class CurrentLabel extends Label {
    public CurrentLabel() {
      super(null, null, null, null);
    }

    @Override
    public String getName() {
      return "current";
    }

    @Override
    public Entry getEntry() {
      // todo what about timestamp?
      return getVcsEntry().withContent(getCurrentContent(), null);
    }
  }
}
