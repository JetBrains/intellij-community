package org.hanuna.gitalk.graph.elements;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class Branch {
    private final Hash upCommitHash;
    private final Hash downCommitHash;

    public Branch(@NotNull Hash upCommitHash, @NotNull Hash downCommitHash) {
        this.upCommitHash = upCommitHash;
        this.downCommitHash = downCommitHash;
    }

    public Branch(@NotNull Hash commit) {
        this(commit, commit);
    }

    @NotNull
    public Hash getUpCommitHash() {
        return upCommitHash;
    }

    @NotNull
    public Hash getDownCommitHash() {
        return downCommitHash;
    }

    public int getBranchNumber() {
        return upCommitHash.hashCode() + 73 * downCommitHash.hashCode();
    }

    @Override
    public int hashCode() {
        return upCommitHash.hashCode() + 73 * downCommitHash.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == Branch.class) {
            Branch anBranch = (Branch) obj;
            return anBranch.upCommitHash == upCommitHash && anBranch.downCommitHash == downCommitHash;
        }
        return false;
    }

    @Override
    public String toString() {
        if (upCommitHash == downCommitHash) {
            return upCommitHash.toStrHash();
        } else {
            return upCommitHash.toStrHash() + '#' + downCommitHash.toStrHash();
        }
    }
}
