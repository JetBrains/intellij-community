// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.jps.dependency.Maplet;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class Containers {

  public static MapletFactory createPersistentContainerFactory(String rootDirPath) {
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
    private static final int MAX_CACHE_SIZE = 1024; // todo: make configurable?
    private final String myRootDirPath;
    private final List<MultiMaplet<?, ?>> myMultiMaplets = new ArrayList<>();
    private final List<Maplet<?, ?>> myMaplets = new ArrayList<>();

    PersistentMapletFactory(String rootDirPath) {
      myRootDirPath = rootDirPath;
    }

    @Override
    public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      MultiMaplet<K, V> container = new CachingMultiMaplet<>(
        new PersistentMultiMaplet<>(getMapFile(storageName), new ElementKeyDescriptor<>(keyExternalizer), new ElementDataExternalizer<>(valueExternalizer), () -> (Set<V>)new HashSet<V>()), MAX_CACHE_SIZE
      );
      myMultiMaplets.add(container);
      return container;
    }

    @Override
    public <K, V> Maplet<K, V> createMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
      Maplet<K, V> container = new CachingMaplet<>(
        new PersistentMaplet<>(getMapFile(storageName), new ElementKeyDescriptor<>(keyExternalizer), new ElementDataExternalizer<>(valueExternalizer)), MAX_CACHE_SIZE
      );
      myMaplets.add(container);
      return container;
    }

    @Override
    public void close() {
      Throwable ex = null;
      for (Closeable container : Iterators.flat(myMultiMaplets, myMaplets)) {
        try {
          container.close();
        }
        catch (Throwable e) {
          if (ex == null) {
            ex = e;
          }
        }
      }
      myMultiMaplets.clear();
      myMaplets.clear();
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

  private static class ElementDataExternalizer<T> implements DataExternalizer<T> {
    private final Externalizer<T> myExternalizer;

    ElementDataExternalizer(Externalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    @Override
    public void save(@NotNull DataOutput out, T value) throws IOException {
      myExternalizer.save(GraphDataOutput.wrap(out), value);
    }

    @Override
    public T read(@NotNull DataInput in) throws IOException {
      return myExternalizer.load(GraphDataInput.wrap(in));
    }
  }

  private static class ElementKeyDescriptor<T> extends ElementDataExternalizer<T> implements KeyDescriptor<T> {

    ElementKeyDescriptor(Externalizer<T> externalizer) {
      super(externalizer);
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
