package org.hanuna.gitalk.graph.elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class Branch {
    private final Commit upCommit;
    private final Commit downCommit;

    public Branch(@NotNull Commit upCommit, @NotNull Commit downCommit) {
        this.upCommit = upCommit;
        this.downCommit = downCommit;
    }

    public Branch(Commit commit) {
        this(commit, commit);
    }


    public int getBranchNumber() {
        return upCommit.hashCode() + downCommit.hashCode();
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
