package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

public interface DiffProvider {

  VcsRevisionNumber getCurrentRevision(VirtualFile file);

  VcsRevisionNumber getLastRevision(VirtualFile virtualFile);

  VcsFileContent createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile);
}
