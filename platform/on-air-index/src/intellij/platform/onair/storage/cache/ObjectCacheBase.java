// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.cache;

import org.jetbrains.annotations.NotNull;

public abstract class ObjectCacheBase<K, V> {

  public static final int DEFAULT_SIZE = 8192;
  public static final int MIN_SIZE = 4;

  static final CriticalSection TRIVIAL_CRITICAL_SECTION = new CriticalSection() {
    @Override
    public void close() {
    }
  };

  protected final int size;
  private final CriticalSection criticalSection = new CriticalSection() {

    @Override
    public void close() {
      unlock();
    }
  };

  protected ObjectCacheBase(final int size) {
    this.size = Math.max(MIN_SIZE, size);
  }

  public boolean isEmpty() {
    return count() == 0;
  }

  public int size() {
    return size;
  }

  public boolean containsKey(final K key) {
    return isCached(key);
  }

  public V get(final K key) {
    return tryKey(key);
  }

  public boolean isCached(final K key) {
    return getObject(key) != null;
  }

  @SuppressWarnings({"VariableNotUsedInsideIf"})
  public V put(final K key, final V value) {
    final V oldValue = tryKey(key);
    if (oldValue != null) {
      remove(key);
    }
    cacheObject(key, value);
    return oldValue;
  }

  public V tryKeyLocked(@NotNull final K key) {
    try (CriticalSection ignored = newCriticalSection()) {
      return tryKey(key);
    }
  }

  public abstract void clear();

  public abstract void lock();

  public abstract void unlock();

  public abstract V cacheObject(@NotNull final K key, @NotNull final V x);

  // returns value pushed out of the cache
  public abstract V remove(@NotNull final K key);

  public abstract V tryKey(@NotNull final K key);

  public abstract V getObject(@NotNull final K key);

  public abstract int count();

  /**
   * Formats hit rate in percent with one decimal place.
   *
   * @param hitRate hit rate value in the interval [0..1]
   */
  public static String formatHitRate(final float hitRate) {
    final int result = (int) (hitRate * 1000);
    return String.valueOf((result / 10)) + '.' + (result % 10) + '%';
  }

  public CriticalSection newCriticalSection() {
    lock();
    return criticalSection;
  }

  public interface CriticalSection extends AutoCloseable {

    @Override
    void close();
  }
}
