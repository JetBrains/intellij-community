package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
class MutableCommit implements Commit {
    private final Hash hash;
    private CommitData commitData = null;

    public MutableCommit(@NotNull Hash hash) {
        this.hash = hash;
    }

    public void setCommitData(@NotNull CommitData commitData) {
        this.commitData = commitData;
    }

    @NotNull
    @Override
    public Hash hash() {
        return hash;
    }

    @Nullable
    @Override
    public CommitData getData() {
        return commitData;
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj != null && obj instanceof Commit) {
            Commit an = (Commit) obj;
            return hash.equals(an.hash());
        }
        return false;
    }
}
