package com.intellij.vcs.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
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
   * Reads {@link #COMMIT_BLOCK_SIZE the first part} of the log.
   */
  @NotNull
  List<? extends VcsCommitDetails> readFirstBlock(@NotNull VirtualFile root, boolean ordered) throws VcsException;

  /**
   * Reads the whole history, but only hashes & parents.
   */
  @NotNull
  List<TimeCommitParents> readAllHashes(@NotNull VirtualFile root) throws VcsException;

  /**
   * Reads those details of the given commits, which are necessary to be shown in the log table.
   */
  @NotNull
  List<? extends VcsCommit> readMiniDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  /**
   * Read full details of the given commits from the VCS.
   */
  @NotNull
  List<? extends VcsCommitDetails> readDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException;

  /**
   * Read all references (branches, tags, etc.) for the given roots.
   */
  Collection<Ref> readAllRefs(@NotNull VirtualFile root) throws VcsException;

  /**
   * Returns the VCS which is supported by this provider.
   */
  @NotNull
  VcsKey getSupportedVcs();

}
