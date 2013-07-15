package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitParents implements CommitParents {
  private final Hash commitHash;
  private final List<Hash> parentHashes;

  public SimpleCommitParents(Hash commitHash, List<Hash> parentHashes) {
    this.commitHash = commitHash;
    this.parentHashes = parentHashes;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return commitHash;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return parentHashes;
  }
}
