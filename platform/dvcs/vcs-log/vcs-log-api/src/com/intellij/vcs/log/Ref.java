package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class Ref {
  private final Hash commitHash;
  private final String name;
  private final RefType type;
  private final VirtualFile myRoot;

  public Ref(@NotNull Hash commitHash, @NotNull String name, @NotNull RefType type, @NotNull VirtualFile root) {
    this.commitHash = commitHash;
    this.name = name;
    this.type = type;
    myRoot = root;
  }

  @NotNull
  public RefType getType() {
    return type;
  }

  @NotNull
  public Hash getCommitHash() {
    return commitHash;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getShortName() {
    int ind = name.lastIndexOf("/");
    return name.substring(ind + 1);
  }

  @Override
  public String toString() {
    return "Ref{" +
           "commitHash=" + commitHash +
           ", name='" + name + '\'' +
           ", type=" + type +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Ref ref = (Ref)o;

    if (commitHash != null ? !commitHash.equals(ref.commitHash) : ref.commitHash != null) return false;
    if (name != null ? !name.equals(ref.name) : ref.name != null) return false;
    if (type != ref.type) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = commitHash != null ? commitHash.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    return result;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  public enum RefType {
    LOCAL_BRANCH,
    BRANCH_UNDER_INTERACTIVE_REBASE,
    REMOTE_BRANCH,
    TAG,
    STASH,
    ANOTHER,
    HEAD;

    public boolean isBranch() {
      return this == LOCAL_BRANCH || this == BRANCH_UNDER_INTERACTIVE_REBASE || this == REMOTE_BRANCH || this == HEAD;
    }

    public boolean isLocalOrHead() {
      return this == LOCAL_BRANCH || this == HEAD;
    }

  }
}
