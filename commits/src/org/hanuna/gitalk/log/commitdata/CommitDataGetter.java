package org.hanuna.gitalk.log.commitdata;

import org.hanuna.gitalk.log.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitDataGetter {
    @NotNull
    public CommitData getCommitData(@NotNull Hash commit);

    public boolean wasLoadData(@NotNull Hash commit);

    public void preLoadCommitData(@NotNull List<Hash> commitHashes);
}
