// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.io.FileUtil;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.SerializableGraphElement;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
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
    public <K extends SerializableGraphElement, V extends SerializableGraphElement> MultiMaplet<K, V> createSetMultiMaplet(String storageName) {
      return new MemorySetMultiMaplet<>();
    }
  };

  public static <K, V> Map<K, V> createCustomPolicyMap(BiFunction<? super K, ? super K, Boolean> keyEqualsImpl, Function<? super K, Integer> keyHashCodeImpl) {
    return new Object2ObjectOpenCustomHashMap<>(asStrategy(keyEqualsImpl, keyHashCodeImpl));
  }

  public static <T> Set<T> createCustomPolicySet(BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
    return new ObjectOpenCustomHashSet<>(asStrategy(equalsImpl, hashCodeImpl));
  }

  public static <T> Set<T> createCustomPolicySet(Collection<? extends T> col, BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
    return new ObjectOpenCustomHashSet<>(col, asStrategy(equalsImpl, hashCodeImpl));
  }

  private static @NotNull <T> Hash.Strategy<T> asStrategy(BiFunction<? super T, ? super T, Boolean> equalsImpl, Function<? super T, Integer> hashCodeImpl) {
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

    private final String myRootDirPath;
    private final List<PersistentSetMultiMaplet<?, ?>> myContainers = new ArrayList<>();

    PersistentMapletFactory(String rootDirPath) {
      myRootDirPath = rootDirPath;
    }

    @Override
    public <K extends SerializableGraphElement, V extends SerializableGraphElement> MultiMaplet<K, V> createSetMultiMaplet(String storageName) {
      PersistentSetMultiMaplet<K, V> container = new PersistentSetMultiMaplet<>(getMapFile(storageName));
      myContainers.add(container);
      return container;
    }

    @Override
    public void close() {
      Throwable ex = null;
      for (PersistentSetMultiMaplet<?, ?> container : myContainers) {
        try {
          container.close();
        }
        catch (Throwable e) {
          if (ex == null) {
            ex = e;
          }
        }
      }
      myContainers.clear();
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
}
