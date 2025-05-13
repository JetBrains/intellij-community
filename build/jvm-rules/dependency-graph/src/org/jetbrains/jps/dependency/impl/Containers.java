// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.jps.dependency.Maplet;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Containers {

  public static final MapletFactory MEMORY_CONTAINER_FACTORY = new MapletFactory() {
    @Override
    public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      return new MemoryMultiMaplet<>(() -> (Set<V>)new HashSet<V>());
    }

    @Override
    public <K, V> Maplet<K, V> createMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      return new MemoryMaplet<>();
    }
  };

  public static <K, V> Map<K, V> createCustomPolicyMap(BiFunction<? super K, ? super K, Boolean> keyEqualsImpl, Function<? super K, Integer> keyHashCodeImpl) {
    return new Object2ObjectOpenCustomHashMap<>(asHashStrategy(keyEqualsImpl, keyHashCodeImpl));
  }

  public static <T> Set<T> createCustomPolicySet(BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
    return new ObjectOpenCustomHashSet<>(asHashStrategy(equalsImpl, hashCodeImpl));
  }

  public static <T> Set<T> createCustomPolicySet(Collection<? extends T> col, BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
    return new ObjectOpenCustomHashSet<>(col, asHashStrategy(equalsImpl, hashCodeImpl));
  }

  private static @NotNull <T> Hash.Strategy<T> asHashStrategy(BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
    return new Hash.Strategy<>() {
      @Override
      public int hashCode(@Nullable T o) {
        return hashCodeImpl.apply(o);
      }

      @Override
      public boolean equals(@Nullable T a, @Nullable T b) {
        return equalsImpl.apply(a, b);
      }
    };
  }

}
