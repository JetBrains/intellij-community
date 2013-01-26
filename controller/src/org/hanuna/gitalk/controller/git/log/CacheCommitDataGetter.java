package org.hanuna.gitalk.controller.git.log;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.controller.git.log.readers.CommitDataReader;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.CommitDataGetter;
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
    }, 1000);
    private final CommitDataReader commitDataReader = new CommitDataReader();

    @NotNull
    @Override
    public CommitData getCommitData(@NotNull Commit commit) {
        return cache.get(commit.hash());
    }

    @Override
    public boolean wasLoadData(@NotNull Commit commit) {
        return cache.containsKey(commit.hash());
    }

    @Override
    public void preLoadCommitData(@NotNull List<Commit> commits) {
        StringBuilder s = new StringBuilder();
        for (Commit commit : commits) {
            s.append(commit.hash().toStrHash()).append(" ");
        }
        List<CommitData> commitDatas = commitDataReader.readCommitsData(s.toString());

        if (commits.size() != commitDatas.size()) {
            throw new IllegalArgumentException("size commits & commitDatas not equals: "
                    + commits.size() + ", " + commitDatas.size());
        }
        for (int i = 0; i < commits.size(); i++) {
            cache.addToCache(commits.get(i).hash(), commitDatas.get(i));
        }
    }


    @NotNull
    private CommitData readCommitData(@NotNull Hash hash) {
        return commitDataReader.readCommitData(hash.toStrHash());
    }
}
