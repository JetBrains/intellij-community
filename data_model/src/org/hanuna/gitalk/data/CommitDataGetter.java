package org.hanuna.gitalk.data;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitData;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitDataGetter {
    @NotNull
    public CommitData getCommitData(@NotNull Node node);

}
