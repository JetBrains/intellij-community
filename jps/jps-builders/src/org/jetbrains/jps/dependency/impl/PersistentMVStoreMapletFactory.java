// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.SystemProperties;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.*;

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

// suitable for relatively small amounts of stored data
public final class PersistentMVStoreMapletFactory implements MapletFactory, Closeable, Flushable {
  private static final int BASE_CACHE_SIZE = 512 * (SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false)? 2 : 1);
  private static final int ALLOWED_STORE_COMPACTION_TIME_MS = -1; // -1 for full-compact, 0 to disable compaction
  private final MVSEnumerator myEnumerator;
  private final Function<Object, Object> myDataInterner;
  private final LoadingCache<Object, Object> myInternerCache;
  //private final LoadingCache<String, String> myStringsInternerCache;
  private final LowMemoryWatcher myMemWatcher;
  private final int myCacheSize;
  private final MVStore myStore;

  public PersistentMVStoreMapletFactory(String filePath, int maxBuilderThreads) throws IOException {
    Files.createDirectories(Path.of(filePath).getParent());
    // todo: need transaction store for transactions?
    myStore = new MVStore.Builder()
      .fileName(filePath)
      .autoCommitDisabled() // all read-write operations are expected to be initiated via Graph APIs, otherwise deadlocks are possible because of incorrect lock acquisition sequence
      .cacheSize(8)
      .compress()
      .cacheConcurrency(getConcurrencyLevel(maxBuilderThreads))
      .open();
    myStore.setVersionsToKeep(0);

    // MVStore counter-based enumerator?
    myEnumerator = new MVSEnumerator(myStore);
    final int maxGb = (int) (Runtime.getRuntime().maxMemory() / 1_073_741_824L);
    myCacheSize = BASE_CACHE_SIZE * Math.min(Math.max(1, maxGb), 5); // increase by BASE_CACHE_SIZE for every additional Gb

    myInternerCache = Caffeine.newBuilder().maximumSize(myCacheSize).build(key -> key);
    //myStringsInternerCache = Caffeine.newBuilder().maximumSize(myCacheSize).build(key -> key);
    myDataInterner = elem -> elem instanceof Usage? myInternerCache.get(elem) : /*elem instanceof String? myStringsInternerCache.get((String) elem) :*/ elem;
    myMemWatcher = LowMemoryWatcher.register(() -> {
      myInternerCache.invalidateAll();
      //myStringsInternerCache.invalidateAll();
      flush();
    });
  }

  private static int getConcurrencyLevel(int builderThreads) {
    int result = 1, next = 1;
    while (next <= builderThreads) {
      result = next;
      next *= 2;
    }
    return result;
  }

  @Override
  public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
    PersistentMVStoreMultiMaplet<K, V, Set<V>> maplet = new PersistentMVStoreMultiMaplet<K, V, Set<V>>(
      myStore, storageName, new GraphDataType<>(keyExternalizer, myEnumerator, myDataInterner), new GraphDataType<>(valueExternalizer, myEnumerator, myDataInterner), HashSet::new, Set[]::new
    );
    return new CachingMultiMaplet<>(maplet, myCacheSize);
  }

  @Override
  public <K, V> Maplet<K, V> createMaplet(String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer) {
    PersistentMVStoreMaplet<K, V> maplet = new PersistentMVStoreMaplet<>(
      myStore, storageName, new GraphDataType<>(keyExternalizer, myEnumerator, myDataInterner), new GraphDataType<>(valueExternalizer, myEnumerator, myDataInterner)
    );
    return new CachingMaplet<>(maplet, myCacheSize);
  }

  @Override
  public void close() {
    myMemWatcher.stop();
    Throwable ex = null;
    try {
      myStore.commit();// first commit all open maps, that might use enumerator for serialization
      myEnumerator.flush(); // save enumerator state
      myStore.close(ALLOWED_STORE_COMPACTION_TIME_MS); // completely close the store commiting the rest of unsaved data
    }
    catch (Throwable e) {
      ex = e;
    }
    myInternerCache.invalidateAll();
    if (ex instanceof IOException) {
      throw new BuildDataCorruptedException((IOException) ex);
    }
    else if (ex != null) {
      throw new RuntimeException(ex);
    }
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
    private final Externalizer<T> myExternalizer;
    private final @Nullable Enumerator myEnumerator;
    private final @Nullable Function<Object, Object> myObjectInterner;

    GraphDataType(Externalizer<T> externalizer, @Nullable Enumerator enumerator, @Nullable Function<Object, Object> objectInterner) {
      myExternalizer = externalizer;
      myEnumerator = enumerator;
      myObjectInterner = objectInterner;
    }

    @Override
    public int compare(T a, T b) {
      return a.toString().compareTo(b.toString());
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
    private final Object2IntMap<String> myToIntMap = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<String> myToStringMap = new Int2ObjectOpenHashMap<>();
    private final MVMap<String, Integer> myStoreMap;
    private Object2IntMap<String> myDelta = new Object2IntOpenHashMap<>();

    MVSEnumerator(MVStore store) {
      myStoreMap = store.openMap("string-table");
      for (Map.Entry<String, Integer> entry : myStoreMap.entrySet()) {
        String str = entry.getKey();
        int num = entry.getValue();
        myToIntMap.put(str, num);
        myToStringMap.put(num, str);
      }
    }

    @Override
    public synchronized String toString(int num) {
      return myToStringMap.get(num);
    }

    @Override
    public synchronized int toNumber(String str) {
      int currentSize = myToIntMap.size();
      int num = myToIntMap.getOrDefault(str, currentSize);
      if (num == currentSize) { // not in map yet
        myToIntMap.put(str, num);
        myToStringMap.put(num, str);
        myDelta.put(str, num);
      }
      return num;
    }

    public boolean flush() {
      Object2IntMap<String> delta;
      synchronized (this) {
        if (myDelta.isEmpty()) {
          return false;
        }
        delta = myDelta;
        myDelta = new Object2IntOpenHashMap<>();
      }
      myStoreMap.putAll(delta);
      return true;
    }
  }
}
