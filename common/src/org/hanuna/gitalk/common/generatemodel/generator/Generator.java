package org.hanuna.gitalk.common.generatemodel.generator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Generator<T> {
    /**
     * @exception NoNext
     */
    @NotNull
    public T generate(T prev, int steps);
}
