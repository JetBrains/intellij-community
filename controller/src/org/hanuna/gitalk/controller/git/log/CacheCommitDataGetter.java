package org.hanuna.gitalk.controller.git.log;

import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.controller.git.log.readers.CommitDataReader;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commitdata.CommitData;
import org.hanuna.gitalk.log.commitdata.CommitDataGetter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    }, 5000);
    private final CommitDataReader commitDataReader = new CommitDataReader();

    @NotNull
    @Override
    public CommitData getCommitData(@NotNull Hash commitHash) {
        return cache.get(commitHash);
    }

    @Override
    public boolean wasLoadData(@NotNull Hash commitHash) {
        return cache.containsKey(commitHash);
    }

    @Override
    public void preLoadCommitData(@NotNull List<Hash> commitHashes) {
        StringBuilder s = new StringBuilder();
        for (Hash commitHash : commitHashes) {
            assert commitHash != null : "null commitHash in preLoad List";
            s.append(commitHash.toStrHash()).append(" ");
        }
        List<CommitData> commitDataList = commitDataReader.readCommitsData(s.toString());

        if (commitHashes.size() != commitDataList.size()) {
            throw new IllegalArgumentException("size commitHashes & commitDataList not equals: "
                    + commitHashes.size() + ", " + commitDataList.size());
        }
        for (int i = 0; i < commitHashes.size(); i++) {
            cache.addToCache(commitHashes.get(i), commitDataList.get(i));
        }
    }


    @NotNull
    private CommitData readCommitData(@NotNull Hash hash) {
        return commitDataReader.readCommitData(hash.toStrHash());
    }
}
