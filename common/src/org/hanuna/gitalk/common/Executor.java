package org.hanuna.gitalk.common;

/**
 * @author erokhins
 */
public interface Executor<K> {
    public void execute(K key);
}
