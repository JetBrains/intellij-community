package org.hanuna.gitalk.graph.elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class Branch {
    private final Commit upCommit;
    private final Commit downCommit;

    public Branch(@NotNull Commit upCommit, @NotNull Commit downCommit) {
        this.upCommit = upCommit;
        this.downCommit = downCommit;
    }

    public Branch(@NotNull Commit commit) {
        this(commit, commit);
    }

    @NotNull
    public Commit getUpCommit() {
        return upCommit;
    }

    @NotNull
    public Commit getDownCommit() {
        return downCommit;
    }

    public int getBranchNumber() {
        return upCommit.hashCode() + downCommit.hashCode();
    }

    @Override
    public int hashCode() {
        return upCommit.hashCode() + downCommit.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == Branch.class) {
            Branch anBranch = (Branch) obj;
            return anBranch.upCommit == upCommit && anBranch.downCommit == downCommit;
        }
        return false;
    }

    @Override
    public String toString() {
        if (upCommit == downCommit) {
            return upCommit.hash().toStrHash();
        } else {
            return upCommit.hash().toStrHash() + '#' + downCommit.hash().toStrHash();
        }
    }
}
