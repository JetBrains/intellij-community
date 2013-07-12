package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommit extends CommitParents {

  @Override
  @NotNull
  Hash getHash();

  @NotNull
  String getSubject();

  @NotNull
  String getAuthorName();

  long getAuthorTime();

}
