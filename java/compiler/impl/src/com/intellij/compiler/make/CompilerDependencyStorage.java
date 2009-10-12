/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.make;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class CompilerDependencyStorage<Key> implements Flushable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CompilerDependencyStorage");
  protected final PersistentHashMap<Key, int[]> myMap;
  protected final SLRUCache<Key, IntSet> myCache;
  private Key myKeyToRemove;

  public CompilerDependencyStorage(File file, KeyDescriptor<Key> keyDescriptor, final int cacheSize) throws IOException {
    myMap = new PersistentHashMap<Key, int[]>(file, keyDescriptor, new DataExternalizer<int[]>() {
      public void save(DataOutput out, int[] array) throws IOException {
        out.writeInt(array.length);
        for (int value : array) {
          out.writeInt(value);
        }
      }

      public int[] read(DataInput in) throws IOException {
        final TIntHashSet set = new TIntHashSet();
        DataInputStream stream = (DataInputStream)in;
        while(stream.available() > 0) {
          final int size = stream.readInt();
          final int _size = Math.abs(size);
          for (int idx = 0; idx < _size; idx++) {
            if (size > 0) {
              set.add(stream.readInt());
            }
            else {
              set.remove(stream.readInt());
            }
          }
        }
        return set.toArray();
      }
    });

    myCache = new SLRUCache<Key, IntSet>(cacheSize * 2, cacheSize) {
      @NotNull
      public IntSet createValue(Key key) {
        return new IntSet(key);
      }

      protected void onDropFromCache(Key key, final IntSet set) {
        if (key == myKeyToRemove || !set.isDirty()) {
          return;
        }
        try {
          if (set.needsCompacting()) {
            myMap.put(key, set.getValues());
          }
          else {
            myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
              public void append(final DataOutput out) throws IOException {
                final Ref<IOException> exception = new Ref<IOException>(null);
                final TIntProcedure saveProc = new TIntProcedure() {
                  public boolean execute(int value) {
                    try {
                      out.writeInt(value);
                      return true;
                    }
                    catch (IOException e) {
                      exception.set(e);
                      return false;
                    }
                  }
                };

                out.writeInt(-set.getRemovedCount());
                set.processRemovedValues(saveProc);
                if (exception.get() != null) {
                  throw exception.get();
                }

                out.writeInt(set.getAddedCount());
                set.processAddedValues(saveProc);
                if (exception.get() != null) {
                  throw exception.get();
                }
              }
            });
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    };
  }

  public synchronized void remove(Key key) throws IOException {
    myKeyToRemove = key;
    try {
      myCache.remove(key);
    }
    finally {
      myKeyToRemove = null;
    }
    myMap.remove(key);
  }

  public synchronized void removeValue(Key key, int value) throws IOException {
    final IntSet set = myCache.get(key);
    set.remove(value);
    if (set.needsFlushing()) {
      flush(key);
    }
  }


  public synchronized void addValue(Key key, int value) throws IOException {
    final IntSet set = myCache.get(key);
    set.add(value);
    if (set.needsFlushing()) {
      flush(key);
    }
  }

  public synchronized int[] getValues(Key key) throws IOException {
    return myCache.get(key).getValues();
  }


  public synchronized void flush() throws IOException {
    myCache.clear();
    myMap.force();
  }

  private void flush(Key key) {
    myCache.remove(key); // makes changes into PersistentHashMap
    myMap.force(); // flushes internal caches (which consume memory) and writes unsaved data to disk
  }

  public synchronized void dispose() {
    try {
      flush();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private class IntSet {
    private final TIntHashSet myAdded = new TIntHashSet();
    private final TIntHashSet myRemoved = new TIntHashSet();
    private TIntHashSet myMerged = null;
    private final Key myKey;

    public IntSet(Key key) {
      myKey = key;
    }

    public void add(int value) {
      if (myMerged != null) {
        myMerged.add(value);
      }
      if (!myRemoved.remove(value)) {
        myAdded.add(value);
      }
    }

    public void remove(int value) {
      if (myMerged != null) {
        myMerged.remove(value);
      }
      if (!myAdded.remove(value)) {
        myRemoved.add(value);
      }
    }

    public boolean isDirty() {
      return myAdded.size() > 0 || myRemoved.size() > 0;
    }

    public boolean needsCompacting() {
      return myMerged != null;
    }

    public boolean needsFlushing() {
      return myAdded.size() > 3000 || myRemoved.size() > 3000;
    }

    public int getAddedCount() {
      return myAdded.size();
    }

    public void processAddedValues(final TIntProcedure procedure) {
      myAdded.forEach(procedure);
    }

    public int getRemovedCount() {
      return myRemoved.size();
    }

    public void processRemovedValues(final TIntProcedure procedure) {
      myRemoved.forEach(procedure);
    }

    public int[] getValues() throws IOException {
      return getMerged().toArray();
    }

    private TIntHashSet getMerged() throws IOException {
      if (myMerged == null) {
        myMerged = new TIntHashSet();
        final int[] fromDisk = myMap.get(myKey);
        if (fromDisk != null) {
          myMerged.addAll(fromDisk);
        }
        if (myRemoved.size() > 0) {
          myMerged.removeAll(myRemoved.toArray());
        }
        if (myAdded.size() > 0) {
          myMerged.addAll(myAdded.toArray());
        }
      }
      return myMerged;
    }
  }
}