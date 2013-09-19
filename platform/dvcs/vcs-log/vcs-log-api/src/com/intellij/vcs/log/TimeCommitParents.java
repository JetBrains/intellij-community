package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Hash + parents + the timestamp of the commit
 *
 * @author Kirill Likhodedov
 */
public class TimeCommitParents extends CommitParents {

  private final long myTime;

  public TimeCommitParents(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    super(hash, parents);
    myTime = timeStamp;
  }

  public final long getAuthorTime() {
    return myTime;
  }

  @Override
  public String toString() {
    return super.toString() + ":" + myTime;
  }

}
