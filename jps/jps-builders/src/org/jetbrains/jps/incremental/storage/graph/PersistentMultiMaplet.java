// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.graph;

import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PersistentMultiMaplet<K, V, C extends Collection<V>> implements MultiMaplet<K, V> {
  private final PersistentHashMap<K, C> myMap;
  private final DataExternalizer<V> myValueExternalizer;
  private final C myEmptyCollection;
  private final Supplier<? extends C> myCollectionFactory;

  public PersistentMultiMaplet(Path mapFile, KeyDescriptor<K> keyDescriptor, DataExternalizer<V> valueExternalizer, Supplier<? extends C> collectionFactory) {
    myCollectionFactory = collectionFactory;
    try {
      C col = collectionFactory.get();
      //noinspection unchecked
      myEmptyCollection = col instanceof List? (C)Collections.emptyList() : col instanceof Set? (C)Collections.emptySet() : col;
      
      myValueExternalizer = valueExternalizer;
      
      myMap = PersistentMapBuilder.newBuilder(mapFile, keyDescriptor, new DataExternalizer<C>() {
        @Override
        public void save(@NotNull DataOutput out, C data) throws IOException {
          out.writeInt(data.size());
          for (V value : data) {
            valueExternalizer.save(out, value);
          }
        }

        @Override
        public C read(@NotNull DataInput in) throws IOException {
          C acc = myCollectionFactory.get();
          final DataInputStream stream = (DataInputStream)in;
          while (stream.available() > 0) {
            int size = stream.readInt();
            Consumer<? super V> appender = size >= 0? acc::add : acc::remove;
            size = Math.abs(size);
            while (size-- > 0) {
              appender.accept(valueExternalizer.read(stream));
            }
          }
          return acc;
        }
      }).build();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean containsKey(K key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public @NotNull C get(K key) {
    try {
      C col = myMap.get(key);
      return col != null? col : myEmptyCollection;
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    try {
      //noinspection unchecked
      C data = ensureCollection(values);
      if (data.isEmpty()) {
        myMap.remove(key);
      }
      else {
        myMap.put(key, data);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private C ensureCollection(Iterable<? extends V> seq) {
    if (myEmptyCollection instanceof Set && seq instanceof Set) {
      return (C)seq;
    }
    if (myEmptyCollection instanceof List && seq instanceof List) {
      return (C)seq;
    }
    return Iterators.collect(seq, myCollectionFactory.get());
  }

  @Override
  public void remove(K key) {
    try {
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void appendValue(K key, V value) {
    appendValues(key, Collections.singleton(value));
  }

  @Override
  public void appendValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      try {
        myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
          @Override
          public void append(@NotNull DataOutput out) throws IOException {
            out.writeInt(Iterators.count(values));
            for (V v : values) {
              myValueExternalizer.save(out, v);
            }
          }
        });
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }
  }

  @Override
  public void removeValue(K key, V value) {
    removeValues(key, Collections.singleton(value));
  }

  @Override
  public void removeValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      try {
        myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
          @Override
          public void append(@NotNull DataOutput out) throws IOException {
            out.writeInt(-Iterators.count(values));
            for (V v : values) {
              myValueExternalizer.save(out, v);
            }
          }
        });
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }
  }

  @Override
  public @NotNull Iterable<K> getKeys() {
    try {
      return myMap.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      myMap.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void flush() throws IOException {
    myMap.force();
  }

}
