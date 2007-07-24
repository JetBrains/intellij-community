package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FilePath;

/**
 * @author yole
 */
public class EmptyChangelistBuilder implements ChangelistBuilder {
  public void processChange(final Change change) {
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList) {
  }

  public void processChangeInList(final Change change, final String changeListName) {
  }

  public void processUnversionedFile(final VirtualFile file) {
  }

  public void processLocallyDeletedFile(final FilePath file) {
  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {
  }

  public void processIgnoredFile(final VirtualFile file) {
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
  }

  public boolean isUpdatingUnversionedFiles() {
    return true;
  }
}