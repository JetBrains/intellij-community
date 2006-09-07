package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author max
 */
public interface ChangeProvider {
  void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress);

  List<VcsException> commit(List<Change> changes, String preparedComment);
  List<VcsException> rollbackChanges(List<Change> changes);
  List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files);
  List<VcsException> rollbackMissingFileDeletion(List<FilePath> files);
  List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files);
}
