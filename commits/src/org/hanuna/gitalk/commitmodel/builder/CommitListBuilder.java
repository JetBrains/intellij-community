package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.log.commit.CommitAndParentHashes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<Commit> commits = new ArrayList<Commit>();
    private final Map<Hash, MutableCommit> cache = new HashMap<Hash, MutableCommit>();
    private boolean wasBuild = false;

    @NotNull
    private MutableCommit getCommit(@NotNull Hash hash) {
        MutableCommit commit = cache.get(hash);
        if (commit == null) {
            commit = new MutableCommit(hash);
            cache.put(hash, commit);
        }
        return commit;
    }

    private void removeCommit(@NotNull Hash hash) {
        cache.remove(hash);
    }

    public void append(@NotNull CommitAndParentHashes logCommit) {
        assert ! wasBuild : "builder was run, but append request";
        MutableCommit commit = getCommit(logCommit.getCommitHash());
        List<Commit> parents = new ArrayList<Commit>(logCommit.getParentsHash().size());
        for (Hash hash : logCommit.getParentsHash()) {
            MutableCommit parent = getCommit(hash);
            parents.add(parent);
        }
        removeCommit(logCommit.getCommitHash());
        commit.setParents(parents);
        commits.add(commit);
    }

    public boolean allCommitsFound() {
        return cache.size() == 0;
    }

    // modifiable List
    @NotNull
    public List<Commit> build() {
        wasBuild = true;
        return commits;
    }

}
