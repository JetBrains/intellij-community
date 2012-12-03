package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

/**
* @author erokhins
*/
public interface Get<K, V> {
    @NotNull
    public V get(@NotNull K key);
}
