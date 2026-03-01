package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MVStoreSwapMap<K, V> implements Map<K, V> {
  private final Map<K, V> myDelegate;
  private final MVStore myStore;

  public MVStoreSwapMap(MVMap<K, V> delegate) {
    myDelegate = delegate;
    myStore = delegate.getStore();
  }

  @Override
  public int size() {
    return myDelegate.size();
  }

  @Override
  public boolean isEmpty() {
    return myDelegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return myDelegate.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return myDelegate.containsValue(value);
  }

  @Override
  public V get(Object key) {
    return myDelegate.get(key);
  }

  @Override
  public @Nullable V put(K key, V value) {
    try {
      return myDelegate.put(key, value);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public V remove(Object key) {
    try {
      return myDelegate.remove(key);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    try {
      myDelegate.putAll(m);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public void clear() {
    try {
      myDelegate.clear();
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    try {
      myDelegate.replaceAll(function);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public @Nullable V putIfAbsent(K key, V value) {
    try {
      return myDelegate.putIfAbsent(key, value);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    try {
      return myDelegate.remove(key, value);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    try {
      return myDelegate.replace(key, oldValue, newValue);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public @Nullable V replace(K key, V value) {
    try {
      return myDelegate.replace(key, value);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mappingFunction) {
    try {
      return myDelegate.computeIfAbsent(key, mappingFunction);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public V computeIfPresent(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    try {
      return myDelegate.computeIfPresent(key, remappingFunction);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public V compute(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    try {
      return myDelegate.compute(key, remappingFunction);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public V merge(K key, @NotNull V value, @NotNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    try {
      return myDelegate.merge(key, value, remappingFunction);
    }
    finally {
      myStore.commit();
    }
  }

  @Override
  public @NotNull Set<K> keySet() {
    return Collections.unmodifiableSet(myDelegate.keySet());
  }

  @Override
  public @NotNull Collection<V> values() {
    return Collections.unmodifiableCollection(myDelegate.values());
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    return Collections.unmodifiableSet(myDelegate.entrySet());
  }

}
