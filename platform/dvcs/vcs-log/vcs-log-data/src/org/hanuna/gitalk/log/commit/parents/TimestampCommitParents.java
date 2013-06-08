package org.hanuna.gitalk.log.commit.parents;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.CommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class TimestampCommitParents implements CommitParents {
  private final CommitParents commitParents;
  private final long timestamp;

  public TimestampCommitParents(CommitParents commitParents, long timestamp) {
    this.commitParents = commitParents;
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return commitParents.getHash();
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return commitParents.getParents();
  }

}
