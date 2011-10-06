package org.jetbrains.jps;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Key<T> {
  private static final AtomicInteger ourKeysCounter = new AtomicInteger();
  private final int myIndex = ourKeysCounter.getAndIncrement();
  private final String myName; // for debug purposes only

  public Key(@NotNull @NonNls String name) {
    myName = name;
  }

  public int hashCode() {
    return myIndex;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }

  public String toString() {
    return myName;
  }

  public static <T> Key<T> create(@NotNull @NonNls String name) {
    return new Key<T>(name);
  }

  @Nullable
  public T get(@Nullable UserDataHolder holder) {
    return holder == null ? null : holder.getUserData(this);
  }

  @Nullable
  public T get(@Nullable Map<Key, Object> holder) {
    return holder == null ? null : (T)holder.get(this);
  }

  public T get(@Nullable UserDataHolder holder, T defaultValue) {
    final T t = get(holder);
    return t == null ? defaultValue : t;
  }

  /**
   * Returns <code>true</code> if and only if the <code>holder</code> has
   * not null value by the key.
   *
   * @param holder user data holder object
   * @return <code>true</code> if holder.getUserData(this) != null
   * <code>false</code> otherwise.
   */
  public boolean isIn(@Nullable UserDataHolder holder) {
    return get(holder) != null;
  }

  public void set(@Nullable UserDataHolder holder, T value) {
    if (holder != null) {
      holder.putUserData(this, value);
    }
  }

  public void set(@Nullable Map<Key, Object> holder, T value) {
    if (holder != null) {
      holder.put(this, value);
    }
  }
}