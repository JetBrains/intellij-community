package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommit extends TimeCommitParents {

  @Override
  @NotNull
  Hash getHash();

  @NotNull
  String getSubject();

  @NotNull
  String getAuthorName();

}
