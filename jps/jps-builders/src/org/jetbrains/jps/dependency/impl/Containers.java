// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.javac.Iterators;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Containers {

  public static MapletFactory createPersistentContainerFactory(String rootDirPath) throws IOException {
    return new PersistentMapletFactory(rootDirPath);
  }

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

  private static class PersistentMapletFactory implements MapletFactory, Closeable {
    private static final int CACHE_SIZE = 512; // todo: make configurable?
    private final String myRootDirPath;
    private final PersistentStringEnumerator myStringTable;
    private final List<Closeable> myMaps = new ArrayList<>();
    private final Enumerator myEnumerator;
    private final Function<Object, Object> myDataInterner;
    private final LoadingCache<Object, Object> myInternerCache;

    PersistentMapletFactory(String rootDirPath) throws IOException {
      myRootDirPath = rootDirPath;
      myStringTable = new PersistentStringEnumerator(getMapFile("string-table"), 1024 * 4, true, new StorageLockContext());
      myEnumerator = new Enumerator() {
        @Override
        public String toString(int num) throws IOException {
          return myStringTable.valueOf(num);
        }

        @Override
        public int toNumber(String str) throws IOException {
          return myStringTable.enumerate(str);
        }
      };
      myInternerCache = Caffeine.newBuilder().maximumSize(2 * CACHE_SIZE).build(key -> key);
      myDataInterner = elem -> {
        return elem instanceof Usage || elem instanceof String? myInternerCache.get(elem) : elem;
      };
    }

    @Override
    public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      MultiMaplet<K, V> container = new CachingMultiMaplet<>(
        new PersistentMultiMaplet<>(getMapFile(storageName), new GraphKeyDescriptor<>(keyExternalizer, myEnumerator), new GraphDataExternalizer<>(valueExternalizer, myEnumerator, myDataInterner), () -> (Set<V>)new HashSet<V>()),
        CACHE_SIZE
      );
      myMaps.add(container);
      return container;
    }

    @Override
    public <K, V> Maplet<K, V> createMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      Maplet<K, V> container = new CachingMaplet<>(
        new PersistentMaplet<>(getMapFile(storageName), new GraphKeyDescriptor<>(keyExternalizer, myEnumerator), new GraphDataExternalizer<>(valueExternalizer, myEnumerator, myDataInterner)),
        CACHE_SIZE
      );
      myMaps.add(container);
      return container;
    }

    @Override
    public void close() {
      Throwable ex = null;
      for (Closeable container : Iterators.flat(myMaps, Iterators.asIterable(myStringTable))) {
        try {
          container.close();
        }
        catch (Throwable e) {
          if (ex == null) {
            ex = e;
          }
        }
      }
      myMaps.clear();
      myInternerCache.invalidateAll();
      if (ex instanceof IOException) {
        throw new BuildDataCorruptedException((IOException)ex);
      }
      else if (ex != null) {
        throw new RuntimeException(ex);
      }
    }

    private Path getMapFile(final String name) {
      final File file = new File(myRootDirPath, name);
      FileUtil.createIfDoesntExist(file);
      return file.toPath();
    }
  }

  private static class GraphDataExternalizer<T> implements DataExternalizer<T> {
    private final Externalizer<T> myExternalizer;
    @Nullable
    private final Enumerator myEnumerator;
    @Nullable
    private final Function<Object, Object> myObjectInterner;

    GraphDataExternalizer(Externalizer<T> externalizer, @Nullable Enumerator enumerator, @Nullable Function<Object, Object> objectInterner) {
      myExternalizer = externalizer;
      myEnumerator = enumerator;
      myObjectInterner = objectInterner;
    }

    @Override
    public void save(@NotNull DataOutput out, T value) throws IOException {
      myExternalizer.save(GraphDataOutputImpl.wrap(out, myEnumerator), value);
    }

    @Override
    public T read(@NotNull DataInput in) throws IOException {
      return myExternalizer.load(GraphDataInputImpl.wrap(in, myEnumerator, myObjectInterner));
    }
  }

  private static class GraphKeyDescriptor<T> extends GraphDataExternalizer<T> implements KeyDescriptor<T> {

    GraphKeyDescriptor(Externalizer<T> externalizer, @Nullable Enumerator enumerator) {
      super(externalizer, enumerator, null);
    }

    @Override
    public boolean isEqual(T val1, T val2) {
      return val1.equals(val2);
    }

    @Override
    public int getHashCode(T value) {
      return value.hashCode();
    }
  }

}
