package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * Interface for performing VCS rollback / revert operations.
 *
 * @author yole
 * @since 7.0
 */
public interface RollbackEnvironment {
  String getRollbackOperationName();
  List<VcsException> rollbackChanges(List<Change> changes);
  List<VcsException> rollbackMissingFileDeletion(List<FilePath> files);
  List<VcsException> rollbackModifiedWithoutCheckout(List<VirtualFile> files);
  void rollbackIfUnchanged(VirtualFile file);
}
