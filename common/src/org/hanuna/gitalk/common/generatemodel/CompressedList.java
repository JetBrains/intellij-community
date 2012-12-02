package org.hanuna.gitalk.common.generatemodel;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CompressedList<T> {
    @NotNull
    public ReadOnlyList<T> getList();

    public void recalculate(@NotNull Replace replace);
}
