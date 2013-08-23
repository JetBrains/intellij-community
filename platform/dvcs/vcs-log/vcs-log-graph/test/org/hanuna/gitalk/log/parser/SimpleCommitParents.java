package org.hanuna.gitalk.log.parser;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
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

  @Override
  public String toString() {
    return commitHash + "|-" + StringUtil.join(parentHashes, ",");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SimpleCommitParents parents = (SimpleCommitParents)o;

    if (commitHash != null ? !commitHash.equals(parents.commitHash) : parents.commitHash != null) return false;
    if (parentHashes != null ? !parentHashes.equals(parents.parentHashes) : parents.parentHashes != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = commitHash != null ? commitHash.hashCode() : 0;
    result = 31 * result + (parentHashes != null ? parentHashes.hashCode() : 0);
    return result;
  }
}
