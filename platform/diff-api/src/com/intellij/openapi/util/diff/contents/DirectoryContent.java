package com.intellij.openapi.util.diff.contents;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface DirectoryContent extends DiffContent {
  /**
   * @return VirtualFile from which this content gets data.
   */
  @NotNull
  VirtualFile getFile();
}
