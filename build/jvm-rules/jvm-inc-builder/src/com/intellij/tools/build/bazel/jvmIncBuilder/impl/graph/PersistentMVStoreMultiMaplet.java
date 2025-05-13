// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;
import org.h2.mvstore.type.DataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PersistentMVStoreMultiMaplet<K, V, C extends Collection<V>> implements MultiMaplet<K, V> {
  private final MVMap<K, C> myMap;
  private final C myEmptyCollection;
  private final Supplier<? extends C> myCollectionFactory;
  private final MVMap.DecisionMaker<C> myAppendDecisionMaker;
  private final MVMap.DecisionMaker<C> myRemoveDecisionMaker;

  public PersistentMVStoreMultiMaplet(MVStore store, String mapName, DataType<K> keyType, DataType<V> valueType, Supplier<? extends C> collectionFactory, Function<Integer, C[]> collectionArrayFactory) {
    myCollectionFactory = collectionFactory;
    try {
      C col = collectionFactory.get();
      //noinspection unchecked
      myEmptyCollection = col instanceof List? (C)Collections.emptyList() : col instanceof Set? (C)Collections.emptySet() : col;
      if (col instanceof Set) {
        myAppendDecisionMaker = new AppendSetDecisionMaker();
        myRemoveDecisionMaker = new RemoveSetDecisionMaker();
      }
      else {
        myAppendDecisionMaker = new AppendDecisionMaker();
        myRemoveDecisionMaker = new RemoveDecisionMaker();
      }
      MVMap.Builder<K, C> mapBuilder = new MVMap.Builder<K, C>().keyType(keyType).valueType(new BasicDataType<>() {
        @Override
        public boolean isMemoryEstimationAllowed() {
          return false;
        }

        @Override
        public int getMemory(C obj) {
          return 0;
        }

        @Override
        public void write(WriteBuffer buff, C col) {
          buff.putInt(col.size());
          for (V value : col) {
            valueType.write(buff, value);
          }
        }

        @Override
        public C read(ByteBuffer buff) {
          C acc = myCollectionFactory.get();
          int size = buff.getInt();
          while (size-- > 0) {
            acc.add(valueType.read(buff));
          }
          return acc;
        }

        @Override
        public C[] createStorage(int size) {
          //noinspection unchecked
          return collectionArrayFactory.apply(size);
        }
      });
      myMap = store.openMap(mapName, mapBuilder);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean containsKey(K key) {
    return myMap.containsKey(key);
  }

  @Override
  public @NotNull C get(K key) {
    C col = myMap.get(key);
    return col != null? col : myEmptyCollection;
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    //noinspection unchecked
    C data = ensureCollection(values);
    if (data.isEmpty()) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, data);
    }
  }

  /** @noinspection unchecked*/
  private C ensureCollection(Iterable<? extends V> seq) {
    if (myEmptyCollection instanceof Set && seq instanceof Set) {
      return (C)seq;
    }
    if (myEmptyCollection instanceof List && seq instanceof List) {
      return (C)seq;
    }
    if (myEmptyCollection.getClass().isInstance(seq)) {
      return (C)seq;
    }
    return Iterators.collect(seq, myCollectionFactory.get());
  }

  @Override
  public void remove(K key) {
    myMap.remove(key);
  }

  @Override
  public void appendValue(K key, V value) {
    appendValues(key, Collections.singleton(value));
  }

  @Override
  public void appendValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      myMap.operate(key, ensureCollection(values), myAppendDecisionMaker);
    }
  }

  @Override
  public void removeValue(K key, V value) {
    removeValues(key, Collections.singleton(value));
  }

  @Override
  public void removeValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      myMap.operate(key, ensureCollection(values), myRemoveDecisionMaker);
    }
  }

  @Override
  public @NotNull Iterable<K> getKeys() {
    return myMap.keySet();
  }

  @Override
  public void close() {
    // no impl;
  }

  @Override
  public void flush() {
    // no impl
  }


  private class AppendDecisionMaker extends MVMap.DecisionMaker<C> {
    @Override
    public MVMap.Decision decide(C existingValue, C providedValue) {
      if (providedValue == null || providedValue.isEmpty()) {
        return MVMap.Decision.ABORT;
      }
      return MVMap.Decision.PUT;
    }

    @Override
    public <T extends C> T selectValue(T existingValue, T providedValue) {
      if (existingValue == null || existingValue.isEmpty()) {
        return providedValue;
      }
      //noinspection unchecked
      T c = (T)myCollectionFactory.get();
      c.addAll(existingValue);
      if (c.addAll(providedValue)) {
        return c;
      }
      return existingValue;
    }
  }

  private class AppendSetDecisionMaker extends AppendDecisionMaker {
    @Override
    public MVMap.Decision decide(C existingValue, C providedValue) {
      if (super.decide(existingValue, providedValue) == MVMap.Decision.ABORT) {
        return MVMap.Decision.ABORT;
      }
      if (existingValue == null || existingValue.isEmpty()) {
        return MVMap.Decision.PUT;
      }
      for (V v : providedValue) {
        if (!existingValue.contains(v)) {
          return MVMap.Decision.PUT;
        }
      }
      return MVMap.Decision.ABORT;
    }
  }

  private class RemoveDecisionMaker extends MVMap.DecisionMaker<C> {
    @Override
    public MVMap.Decision decide(C existingValue, C providedValue) {
      // if nothing to remove or nothing to remove from
      if (providedValue == null || providedValue.isEmpty() || existingValue == null || existingValue.isEmpty()) {
        return MVMap.Decision.ABORT;
      }
      return MVMap.Decision.PUT;
    }

    @Override
    public <T extends C> T selectValue(T existingValue, T providedValue) {
      // both provided and existing values are non-empty collections
      //noinspection unchecked
      T c = (T)myCollectionFactory.get();
      c.addAll(existingValue);
      if (c.removeAll(providedValue)) {
        return c;
      }
      return existingValue;
    }
  }

  private class RemoveSetDecisionMaker extends RemoveDecisionMaker {
    @Override
    public MVMap.Decision decide(C existingValue, C providedValue) {
      if (super.decide(existingValue, providedValue) == MVMap.Decision.ABORT) {
        return MVMap.Decision.ABORT;
      }
      for (V v : providedValue) {
        if (existingValue.contains(v)) {
          return MVMap.Decision.PUT;
        }
      }
      return MVMap.Decision.ABORT;
    }
  }

}
