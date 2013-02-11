package org.hanuna.gitalk.log.commit;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitParents {

    @NotNull
    public Hash getCommitHash();

    @NotNull
    public List<Hash> getParentHashes();

}
