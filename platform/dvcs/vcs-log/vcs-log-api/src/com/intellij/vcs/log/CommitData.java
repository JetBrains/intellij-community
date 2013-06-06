package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * TODO: remove and use just VcsCommit instead, if it will be possible.
 *
 * TODO:
 * Note: the Hash can be different from what we have in VcsCommit.getId(), which is very not obvious.
 * It is needed for the interactive rebase preview, but maybe there is another way to implement this can be.
 *
 * @author erokhins
 */
public class CommitData {

  private final VcsCommit myCommit;

  public CommitData(VcsCommit commit) {
    myCommit = commit;
  }

  public Hash getCommitHash() {
    return myCommit.getHash();
  }

  @NotNull
  public String getMessage() {
    return myCommit.getFullMessage();
  }

  @NotNull
  public String getAuthor() {
    return myCommit.getAuthorName();
  }

  public long getTimeStamp() {
    return myCommit.getAuthorTime();
  }

  public VcsCommit getFullCommit() {
    return myCommit;
  }
}
