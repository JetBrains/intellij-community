package org.hanuna.gitalk.common;

import com.intellij.util.Function;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CacheGet<K, V> extends SLRUCache<K, V> {

  public static final int DEFAULT_SIZE = 100;
  private final Function<K, V> myFunction;

  public CacheGet(@NotNull Function<K, V> getFunction, int size) {
    super(2 * size, 2 * size);
    myFunction = getFunction;
  }

  public CacheGet(@NotNull Function<K, V> getFunction) {
    this(getFunction, DEFAULT_SIZE);
  }

  @NotNull
  @Override
  public V createValue(K key) {
    return myFunction.fun(key);
  }

  public boolean isKeyCached(K key) {
    return getIfCached(key) != null;
  }

}
