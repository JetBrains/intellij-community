package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.List;

/**
 * @author max
 */
public interface ChangeProvider {
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress);

  List<VcsException> commit(List<Change> changes, String preparedComment);
  List<VcsException> rollbackChanges(List<Change> changes);
  List<VcsException> scheduleMissingFileForDeletion(List<File> files);
  List<VcsException> rollbackMissingFileDeletion(List<File> files);
  List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files);
}
