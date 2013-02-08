package org.hanuna.gitalk.common.compressedlist.generator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Generator<T> {
    /**
     * @exception java.util.NoSuchElementException
     * @exception IllegalArgumentException
     */
    @NotNull
    public T generate(@NotNull T prev, int steps);

    @NotNull
    public T generateFirst();
}
