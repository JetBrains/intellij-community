// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.h2.mvstore.FileStore;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.ComparableTypeExternalizer;
import org.jetbrains.jps.dependency.Enumerator;
import org.jetbrains.jps.dependency.Maplet;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.impl.CachingMaplet;
import org.jetbrains.jps.dependency.impl.CachingMultiMaplet;
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl;
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl;
import org.jetbrains.jps.dependency.impl.GraphElementInterner;
import org.jetbrains.jps.dependency.impl.ObjectEnumerator;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.jetbrains.jps.util.Iterators.map;

// suitable for relatively small amounts of stored data
public final class PersistentMVStoreMapletFactory implements MapletFactory, Closeable, Flushable {
  private static final int BASE_CACHE_SIZE = 512;
  private final MVSEnumerator myEnumerator;
  private final Function<Object, Object> myDataInterner;
  private final int myCacheSize;
  private final MVStore myStore;

  private final long myInitialVersion;

  public PersistentMVStoreMapletFactory(String filePath, int maxBuilderThreads) throws IOException {
    Files.createDirectories(Path.of(filePath).getParent());
    myStore = new MVStore.Builder()
      .fileName(filePath)
      .autoCommitDisabled() // all read-write operations are expected to be initiated via Graph APIs, otherwise deadlocks are possible because of incorrect lock acquisition sequence
      .cacheSize(8)
      .compress()
      .cacheConcurrency(getConcurrencyLevel(maxBuilderThreads))
      .open();
    myStore.setVersionsToKeep(0);
    myInitialVersion = myStore.getCurrentVersion();
    // MVStore counter-based enumerator?
    myEnumerator = new MVSEnumerator(myStore);
    final int maxGb = (int) (Runtime.getRuntime().maxMemory() / 1_073_741_824L);
    myCacheSize = BASE_CACHE_SIZE * Math.clamp(maxGb, 1, 5); // increase by BASE_CACHE_SIZE for every additional Gb

    myDataInterner = elem -> {
      if (elem instanceof Usage) {
        return GraphElementInterner.intern((Usage)elem);
      }
      if (elem instanceof ReferenceID) {
        return GraphElementInterner.intern((ReferenceID)elem);
      }
      return elem;
    };
  }

  private static int getConcurrencyLevel(int builderThreads) {
    int result = 1, next = 1;
    while (next <= builderThreads) {
      result = next;
      next *= 2;
    }
    return result;
  }

  public boolean hasUpdates() {
    return myInitialVersion != myStore.getCurrentVersion();
  }

  @Override
  public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, ComparableTypeExternalizer<K> keyExternalizer, ComparableTypeExternalizer<V> valueExternalizer) {
    PersistentMVStoreMultiMaplet<K, V, Set<V>> maplet = new PersistentMVStoreMultiMaplet<K, V, Set<V>>(
      myStore, storageName, new GraphDataType<>(keyExternalizer, myEnumerator, myDataInterner), new GraphDataType<>(valueExternalizer, myEnumerator, myDataInterner), HashSet::new, Set[]::new
    );
    return new CachingMultiMaplet<>(maplet, myCacheSize);
  }

  @Override
  public <K, V> Maplet<K, V> createMaplet(String storageName, ComparableTypeExternalizer<K> keyExternalizer, ComparableTypeExternalizer<V> valueExternalizer) {
    PersistentMVStoreMaplet<K, V> maplet = new PersistentMVStoreMaplet<>(
      myStore, storageName, new GraphDataType<>(keyExternalizer, myEnumerator, myDataInterner), new GraphDataType<>(valueExternalizer, myEnumerator, myDataInterner)
    );
    return new CachingMaplet<>(maplet, myCacheSize);
  }

  @Override
  public void close() {
    myStore.commit();// first commit all open maps, that might use enumerator for serialization
    myEnumerator.flush(); // save enumerator state
    myStore.close(getCompactionTimeMs()); // completely close the store commiting the rest of unsaved data
  }

  /*
  Dynamically calculated max allowed compaction time based on storage fragmentation rate
  special values: -1 for full-compact, 0 to disable compaction
  */
  private int getCompactionTimeMs() {
    FileStore<?> fileStore = myStore.getFileStore();
    int fileFillRate = fileStore.getFillRate();        // File space utilization
    int chunkFillRate = fileStore.getChunksFillRate(); // Chunk packing efficiency

    if (fileFillRate > 80 && chunkFillRate > 80) {
      // File and chunks well utilized, no compaction
      return 0;
    }
    if (fileFillRate > 60 && chunkFillRate > 60) {
      // Moderate fragmentation
      return 100;
    }
    // High fragmentation
    return 300;
  }

  @Override
  public void flush() {
    // maintain consistent state between the content in maps and in the enumerator
    if (myStore.tryCommit() >= 0L) { // first save data from the maps => this may add additional entries to the enumerator.
      if (myEnumerator.flush()) { // then store enumerator data
        myStore.commit(); // if any data were stored, commit finally
      }
    }
  }

  private static class GraphDataType<T> extends BasicDataType<T> {
    private final ComparableTypeExternalizer<T> myExternalizer;
    private final @Nullable Enumerator myEnumerator;
    private final @Nullable Function<Object, Object> myObjectInterner;

    GraphDataType(ComparableTypeExternalizer<T> externalizer, @Nullable Enumerator enumerator, @Nullable Function<Object, Object> objectInterner) {
      myExternalizer = externalizer;
      myEnumerator = enumerator;
      myObjectInterner = objectInterner;
    }

    @Override
    public int compare(T a, T b) {
      return myExternalizer.compare(a, b);
    }

    @Override
    public boolean isMemoryEstimationAllowed() {
      return false; // todo?
    }

    @Override
    public int getMemory(T obj) {
      return 0;
    }

    @Override
    public void write(WriteBuffer buff, T value) {
      try {
        myExternalizer.save(GraphDataOutputImpl.wrap(new WriteBufferDataOutput(buff), myEnumerator), value);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T read(ByteBuffer buff) {
      try {
        return myExternalizer.load(GraphDataInputImpl.wrap(new ByteBufferDataInput(buff), myEnumerator, myObjectInterner));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T[] createStorage(int size) {
      return myExternalizer.createStorage(size);
    }
  }

  private static final class MVSEnumerator implements Enumerator {
    private final ObjectEnumerator<String> myEnumerator;
    // MVMap is a sorted map implementation using a B+ tree. Keys are sorted in their natural ordering.
    private final MVMap<Integer, String> myStoreMap;

    MVSEnumerator(MVStore store) {
      myStoreMap = store.openMap("string-table");
      // expect sequential order in the myStoreMap
      myEnumerator = new ObjectEnumerator<>(map(myStoreMap.entrySet(), Map.Entry::getValue), GraphElementInterner::intern);
    }

    @Override
    public synchronized String toString(int num) throws IOException {
      String str = myEnumerator.lookup(num);
      if (str == null) {
        throw new IOException(
          "Mapping for number " + num + " does not exist. Current string table size: " + myEnumerator.getTableSize() + " entries."
        );
      }
      return str;
    }

    @Override
    public synchronized int toNumber(String str) {
      return myEnumerator.toNumber(str);
    }

    public synchronized boolean flush() {
      try {
        return myEnumerator.drainUnsaved((key, value) -> myStoreMap.put(key, value));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
