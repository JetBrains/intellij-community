package org.hanuna.gitalk.log.commit;

import org.hanuna.gitalk.commitmodel.Commit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitDataGetter {
    @NotNull
    public CommitData getCommitData(@NotNull Commit commit);

    public boolean wasLoadData(@NotNull Commit commit);

    public void preLoadCommitData(@NotNull List<Commit> commits);
}
