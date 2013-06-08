package com.intellij.vcs.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Provides the information needed to build the VCS log, such as the list of most recent commits with their parents.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogProvider {

  int COMMIT_BLOCK_SIZE = 1000;

  /**
   * @return empty list, if all commits was readied
   */
  @NotNull
  List<? extends VcsCommit> readNextBlock(@NotNull VirtualFile root) throws VcsException;

  /**
   * Read details of the given commits from the VCS
   *
   * @param root
   * @param hashes
   * @return
   */
  @NotNull
  List<CommitData> readCommitsData(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  Collection<Ref> readAllRefs(@NotNull VirtualFile root) throws VcsException;
}
