package org.hanuna.gitalk.controller.git_log;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.CommitDataGetter;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CacheCommitDataGetter implements CommitDataGetter {
    private final CacheGet<Hash, CommitData> cache = new CacheGet<Hash, CommitData>(new Get<Hash, CommitData>() {
        @NotNull
        @Override
        public CommitData get(@NotNull Hash key) {
            return readCommitData(key);
        }
    }, 1000);

    @NotNull
    @Override
    public CommitData getCommitData(@NotNull Commit commit) {
        return cache.get(commit.hash());
    }


    @NotNull
    private CommitData readCommitData(@NotNull Hash hash) {
        return null;
    }
}
