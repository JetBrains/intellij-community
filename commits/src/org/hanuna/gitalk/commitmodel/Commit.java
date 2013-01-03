package org.hanuna.gitalk.commitmodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author erokhins
 *
 */
public interface Commit {
    @NotNull
    public Hash hash();

    @Nullable
    public List<Commit> getParents();

}
