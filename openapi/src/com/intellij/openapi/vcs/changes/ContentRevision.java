package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface ContentRevision {
  /**
   * Content of the revision. Implementers are encouraged to lazy implement this especially when it requires connection to the
   * version control server or something.
   * Might return null in case if file path denotes a directory or content is impossible to retreive.
   * @return content of the revision
   */
  @Nullable
  String getContent();

  /**
   * @return file path of the revision
   */
  @NotNull
  FilePath getFile();
}
