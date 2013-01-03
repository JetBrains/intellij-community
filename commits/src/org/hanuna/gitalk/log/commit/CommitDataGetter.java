package org.hanuna.gitalk.log.commit;

import org.hanuna.gitalk.commitmodel.Commit;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitDataGetter {
    @NotNull
    public CommitData getCommitData(@NotNull Commit commit);
}
