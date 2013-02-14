package org.hanuna.gitalk.data;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitData;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitDataGetter {

    // this method support pre-load beside nodes
    @NotNull
    public CommitData getCommitData(@NotNull Node node);

    @NotNull
    public CommitData getCommitData(@NotNull Hash commitHash);
}
