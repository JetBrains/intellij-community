package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Similar to {@link com.intellij.openapi.vcs.history.VcsRevisionDescription}, but contains more data required for the VCS LOG.
 * In the future both interfaces should be merged.
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommit extends CommitParents {

  @NotNull
  String getFullMessage();

  @Override
  @NotNull
  Hash getHash();

  @NotNull
  String getAuthorName();

  long getAuthorTime();

  @Override
  @NotNull
  List<Hash> getParents();

  @NotNull
  Collection<Change> getChanges();
}
