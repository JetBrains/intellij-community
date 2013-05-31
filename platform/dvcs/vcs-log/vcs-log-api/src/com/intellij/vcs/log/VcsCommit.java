package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Similar to {@link com.intellij.openapi.vcs.history.VcsRevisionDescription}, but contains more data required for the VCS LOG.
 * In the future both interfaces should be merged.
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommit {

  @NotNull
  String getFullMessage();

  @NotNull
  String getHash();

  @NotNull
  String getAuthor();

  long getAuthorTime();
}
