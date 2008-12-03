package com.intellij.compiler.make;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;

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
              public void append(DataOutput out) throws IOException {
                final int[] removed = set.getRemovedValued();
                out.writeInt(-removed.length);
                for (int value : removed) {
                  out.writeInt(value);
                }

                final int[] added = set.getAddedValues();
                out.writeInt(added.length);
                for (int value : added) {
                  out.writeInt(value);
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

  public Collection<Key> getAllKeys() throws IOException {
    return myMap.getAllKeysWithExistingMapping();
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
    myCache.get(key).remove(value);
  }

  public synchronized void addValue(Key key, int value) throws IOException {
    myCache.get(key).add(value);
  }

  public int[] getValues(Key key) throws IOException {
    return myCache.get(key).getValues();
  }


  public synchronized void flush() throws IOException {
    myCache.clear();
    myMap.force();
  }

  public synchronized void dispose() {
    try {
      flush();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private class IntSet {
    private final TIntHashSet myAdded = new TIntHashSet();
    private final TIntHashSet myRemoved = new TIntHashSet();
    private TIntHashSet myMerged = null;
    private final Key myKey;

    private IntSet(Key key) {
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

    public int[] getAddedValues() {
      return myAdded.toArray();
    }

    public int[] getRemovedValued() {
      return myRemoved.toArray();
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