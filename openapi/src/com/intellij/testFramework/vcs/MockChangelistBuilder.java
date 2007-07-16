package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class MockChangelistBuilder implements ChangelistBuilder {
  private List<Change> myChanges = new ArrayList<Change>();

  public void processChange(Change change) {
    myChanges.add(change);
  }

  public void processChangeInList(Change change, @Nullable ChangeList changeList) {
    myChanges.add(change);
  }

  public void processChangeInList(Change change, String changeListName) {
    myChanges.add(change);
  }

  public void processUnversionedFile(VirtualFile file) {
  }

  public void processLocallyDeletedFile(FilePath file) {
  }

  public void processModifiedWithoutCheckout(VirtualFile file) {
  }

  public void processIgnoredFile(VirtualFile file) {
  }

  public void processSwitchedFile(VirtualFile file, String branch, final boolean recursive) {
  }

  public boolean isUpdatingUnversionedFiles() {
    return true;
  }

  public List<Change> getChanges() {
    return myChanges;
  }
}