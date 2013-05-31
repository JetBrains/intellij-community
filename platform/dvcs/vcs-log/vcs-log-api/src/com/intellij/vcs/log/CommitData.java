package com.intellij.vcs.log;

import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.Hash;
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
  private final Hash hash;

  public CommitData(VcsCommit commit) {
    myCommit = commit;
    hash = Hash.build(myCommit.getHash());
  }

  public CommitData(VcsCommit commit, Hash hash) {
    myCommit = commit;
    this.hash = hash;
  }

  public Hash getCommitHash() {
    return hash;
  }

  @NotNull
  public String getMessage() {
    return myCommit.getFullMessage();
  }

  @NotNull
  public String getAuthor() {
    return myCommit.getAuthor();
  }

  public long getTimeStamp() {
    return myCommit.getAuthorTime();
  }

  public VcsCommit getFullCommit() {
    return myCommit;
  }
}
