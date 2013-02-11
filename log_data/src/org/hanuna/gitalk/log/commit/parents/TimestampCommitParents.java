package org.hanuna.gitalk.log.commit.parents;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commit.CommitParents;
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
    public Hash getCommitHash() {
        return commitParents.getCommitHash();
    }

    @NotNull
    @Override
    public List<Hash> getParentHashes() {
        return commitParents.getParentHashes();
    }

}
