package org.hanuna.gitalk.log.commit;

import org.hanuna.gitalk.commitmodel.Hash;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitAndParentHashes {
    private final Hash commitHash;
    private final List<Hash> parentsHash;

    public CommitAndParentHashes(Hash commitHash, List<Hash> parentsHash) {
        this.commitHash = commitHash;
        this.parentsHash = parentsHash;
    }

    public Hash getCommitHash() {
        return commitHash;
    }

    public List<Hash> getParentsHash() {
        return parentsHash;
    }
}
