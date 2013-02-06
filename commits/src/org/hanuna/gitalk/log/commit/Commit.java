package org.hanuna.gitalk.log.commit;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class Commit {
    private final Hash commitHash;
    private final List<Hash> parentHashes;

    public Commit(@NotNull Hash commitHash, @NotNull List<Hash> parentHashes) {
        this.commitHash = commitHash;
        this.parentHashes = parentHashes;
    }

    @NotNull
    public Hash getCommitHash() {
        return commitHash;
    }

    @NotNull
    public List<Hash> getParentHashes() {
        return parentHashes;
    }
}
