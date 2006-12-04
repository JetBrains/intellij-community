package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class FileHistoryDialogModel {
  private LocalVcs myVcs;
  private VirtualFile myFile;
  private FileDocumentManager myDocumentManager;
  private int myRightLabel;
  private int myLeftLabel;

  public FileHistoryDialogModel(VirtualFile f, LocalVcs vcs, FileDocumentManager dm) {
    myVcs = vcs;
    myFile = f;
    myDocumentManager = dm;
  }

  public List<String> getLabels() {
    List<String> result = new ArrayList<String>();
    for (Label l : getVcsLabels()) {
      result.add(l.getName());
    }
    return result;
  }

  private List<Label> getVcsLabels() {
    List<Label> result = new ArrayList<Label>();

    addCurrentVersionTo(result);
    result.addAll(myVcs.getLabelsFor(myFile.getPath()));

    return result;
  }

  private void addCurrentVersionTo(List<Label> l) {
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

  public void selectLabels(int first, int second) {
    if (first == second) {
      myRightLabel = 0;
      myLeftLabel = first;
    }
    else {
      myRightLabel = first;
      myLeftLabel = second;
    }
  }

  public String getLeftContent() {
    return getVcsLabels().get(myLeftLabel).getEntry().getContent();
  }

  public String getRightContent() {
    return getVcsLabels().get(myRightLabel).getEntry().getContent();
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
