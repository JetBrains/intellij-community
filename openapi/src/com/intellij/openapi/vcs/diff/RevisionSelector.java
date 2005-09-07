package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface RevisionSelector {
  @Nullable VcsRevisionNumber selectNumber(VirtualFile file); 
}
