package org.hanuna.gitalk.commitmodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 *
 */
public interface Commit {
    @NotNull
    public Hash hash();

    @Nullable
    public CommitData getData();

}
