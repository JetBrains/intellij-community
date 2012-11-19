package org.hanuna.gitalk.common;

/**
* @author erokhins
*/
public interface Get<K, V> {
    public V get(K key);
}
