// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Set of {@link VirtualFile}s optimized for compact storage of very large number of files.
 * <p>
 * Supports optimized {@link Collection#add(Object)} and {@link Collection#addAll(Collection)}
 * without materialization of all containing files.
 * Remove operations are not supported.
 * NOT thread-safe.
 * </p>
 * <p>
 * Use {@link VfsUtilCore#createCompactVirtualFileSet()} to instantiate a new set.
 * </p>
 */
@ApiStatus.Internal
public final class CompactVirtualFileSet extends AbstractSet<VirtualFile> implements VirtualFileSetEx {
  static final int INT_SET_LIMIT = 10;
  static final int BIT_SET_LIMIT = 1000;
  static final int BIT_SET_PARTITION_SIZE = 2048; // 256 bytes partition size
  static final int PARTITIONED_BIT_SET_ID_LIMIT = BIT_SET_PARTITION_SIZE * 16; // max 8kB BitSet size

  // all non-VirtualFileWithId files and first several files (up to INT_SET_LIMIT) are stored here
  // when file set become large (>BIT_SET_LIMIT), they stored as id-set here
  // when file set become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private final Set<VirtualFile> weirdFiles = new HashSet<>();
  private SetStorage storage;
  private boolean frozen;

  CompactVirtualFileSet() {
  }

  CompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    addAll(files);
  }

  /**
   * @deprecated Use {@link VfsUtilCore#createCompactVirtualFileSet()} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  @ApiStatus.Internal
  public CompactVirtualFileSet(@NotNull IntSet fileIds) {
    storage = new IntSetStorage(fileIds);
    if (storage.size() > BIT_SET_LIMIT) {
      convertToBitSet();
    }
  }

  @Override
  @ApiStatus.Internal
  public boolean containsId(int fileId) {
    if (storage != null) {
      return storage.containsId(fileId);
    }
    for (VirtualFile file : weirdFiles) {
      if (file instanceof VirtualFileWithId && ((VirtualFileWithId)file).getId() == fileId) {
        return true;
      }
    }
    return false;
  }

  @Override
  @ApiStatus.Internal
  public int @NotNull [] onlyInternalFileIds() {
    if (storage != null) {
      return storage.toIntArray();
    }
    return weirdFiles
      .stream()
      .filter(f -> f instanceof VirtualFileWithId)
      .mapToInt(f -> ((VirtualFileWithId)f).getId())
      .toArray();
  }

  @Override
  public boolean contains(Object file) {
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      if (storage != null) {
        return storage.containsId(id);
      }
    }
    return weirdFiles.contains(file);
  }

  @Override
  public boolean add(@NotNull VirtualFile file) {
    assertNotFrozen();
    boolean added;
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      if (storage != null) {
        added = storage.add(id);
      }
      else {
        added = weirdFiles.add(file);
        if (weirdFiles.size() > INT_SET_LIMIT) {
          convertToIntSet();
        }
      }
    }
    else {
      added = weirdFiles.add(file);
    }
    return added;
  }

  private void convertToIntSet() {
    storage = new IntSetStorage();
    for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile wf = iterator.next();
      if (wf instanceof VirtualFileWithId) {
        storage.add(((VirtualFileWithId)wf).getId());
        iterator.remove();
      }
    }
  }

  private void convertToBitSet() {
    // If we have too big fileIds convert directly to partitioned bit set
    for (IntIterator iterator = storage.intIterator(); iterator.hasNext(); ) {
      if (iterator.nextInt() > PARTITIONED_BIT_SET_ID_LIMIT) {
        convertToPartitionedBitSet();
        return;
      }
    }
    IntIterator iterator = storage.intIterator();
    storage = new BitSetStorage();
    while (iterator.hasNext()) {
      storage.add(iterator.nextInt());
    }
  }

  private void convertToPartitionedBitSet() {
    IntIterator iterator = storage.intIterator();
    storage = new PartitionedBitSetStorage();
    while (iterator.hasNext()) {
      storage.add(iterator.nextInt());
    }
  }

  @Override
  public boolean remove(Object o) {
    assertNotFrozen();

    if (weirdFiles.remove(o)) {
      return true;
    }
    if (!(o instanceof VirtualFileWithId)) {
      return false;
    }
    int fileId = ((VirtualFileWithId)o).getId();
    if (storage != null) {
      return storage.remove(fileId);
    }
    return false;
  }

  @Override
  public void clear() {
    assertNotFrozen();
    weirdFiles.clear();
    storage = null;
  }


  /**
   * Make unmodifiable
   */
  @Override
  public void freeze() {
    frozen = true;
  }

  @Override
  public @NotNull Set<VirtualFile> freezed() {
    freeze();
    return this;
  }

  @Override
  public boolean process(@NotNull Processor<? super VirtualFile> processor) {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    if (storage != null) {
      IntIterator iterator = storage.intIterator();
      while (iterator.hasNext()) {
        VirtualFile file = virtualFileManager.findFileById(iterator.nextInt());
        if (file != null && !processor.process(file)) {
          return false;
        }
      }
    }
    for (VirtualFile t : weirdFiles) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int size() {
    return (storage != null ? storage.size() : 0) + weirdFiles.size();
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    assertNotFrozen();
    if (c instanceof CompactVirtualFileSet) {
      CompactVirtualFileSet compactVirtualFileSet = (CompactVirtualFileSet)c;
      boolean modified = false;

      if (storage != null) {
        IntIterator iterator = storage.intIterator();
        while (iterator.hasNext()) {
          int id = iterator.nextInt();
          if (!compactVirtualFileSet.containsId(id)) {
            iterator.remove();
            modified = true;
          }
        }
      }

      Iterator<VirtualFile> it = weirdFiles.iterator();
      while (it.hasNext()) {
        VirtualFile file = it.next();
        if (!c.contains(file)) {
          it.remove();
          modified = true;
        }
      }

      return modified;
    }
    else {
      return super.retainAll(c);
    }
  }

  private void assertNotFrozen() {
    if (frozen) {
      throw new IllegalStateException("Must not mutate the set after freeze() was called");
    }
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends VirtualFile> c) {
    assertNotFrozen();
    if (c instanceof CompactVirtualFileSet) {
      boolean modified = false;
      CompactVirtualFileSet setToAdd = (CompactVirtualFileSet)c;
      for (VirtualFile file : setToAdd.weirdFiles) {
        if (add(file)) {
          modified = true;
        }
      }

      if (setToAdd.storage == null) return modified;

      if (storage == null) {
        convertToIntSet();
      }

      IntIterator toAdd = setToAdd.storage.intIterator();
      while (toAdd.hasNext()) {
        int id = toAdd.nextInt();
        storage.add(id);
      }

      return modified;
    }
    else {
      return super.addAll(c);
    }
  }

  @Override
  public @NotNull Iterator<VirtualFile> iterator() {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Iterator<VirtualFile> storageIterator;
    if (storage == null || storage.size() == 0) {
      storageIterator = Collections.emptyIterator();
    }
    else {
      storageIterator = new Iterator<VirtualFile>() {
        final IntIterator iterator = storage.intIterator();

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public VirtualFile next() {
          ProgressManager.checkCanceled();
          return virtualFileManager.findFileById(iterator.nextInt());
        }

        @Override
        public void remove() {
          iterator.remove();
        }
      };
    }
    Iterator<VirtualFile> totalIterator = ContainerUtil.concatIterators(storageIterator, weirdFiles.iterator());

    return new Iterator<VirtualFile>() {
      VirtualFile next;
      Boolean hasNext;

      @Override
      public boolean hasNext() {
        findNext();
        return hasNext;
      }

      private void findNext() {
        if (hasNext == null) {
          hasNext = false;
          while (totalIterator.hasNext()) {
            ProgressManager.checkCanceled();
            VirtualFile t = totalIterator.next();
            if (t != null) {
              next = t;
              hasNext = true;
              break;
            }
          }
        }
      }

      @Override
      public VirtualFile next() {
        findNext();

        if (!hasNext) {
          throw new NoSuchElementException();
        }

        VirtualFile result = next;
        hasNext = null;
        return result;
      }

      @Override
      public void remove() {
        totalIterator.remove();
      }
    };
  }

  private interface SetStorage {

    int size();

    boolean containsId(int id);

    int[] toIntArray();

    boolean add(int id);

    IntIterator intIterator();

    boolean remove(int id);
  }

  // when file set becomes large, they stored as id-set here
  private class IntSetStorage implements SetStorage {
    private final IntSet set;

    IntSetStorage() {
      set = new IntOpenHashSet();
    }

    IntSetStorage(IntSet ids) {
      set = new IntOpenHashSet(ids);
    }

    @Override
    public int size() {
      return set.size();
    }

    @Override
    public boolean containsId(int id) {
      return set.contains(id);
    }

    @Override
    public int[] toIntArray() {
      return set.toIntArray();
    }

    @Override
    public boolean add(int id) {
      boolean result = set.add(id);
      if (set.size() > BIT_SET_LIMIT) {
        convertToBitSet();
      }
      return result;
    }

    @Override
    public boolean remove(int id) {
      return set.remove(id);
    }

    @Override
    public IntIterator intIterator() {
      return set.intIterator();
    }
  }

  // when file sets become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private class BitSetStorage implements SetStorage {
    private final BitSet set = new BitSet();

    @Override
    public int size() {
      return set.cardinality();
    }

    @Override
    public boolean containsId(int id) {
      return set.get(id);
    }

    @Override
    public int[] toIntArray() {
      return set.stream().toArray();
    }

    @Override
    public boolean add(int id) {
      if (set.get(id)) return false;
      if (id > PARTITIONED_BIT_SET_ID_LIMIT) {
        convertToPartitionedBitSet();
        storage.add(id);
      }
      else {
        set.set(id);
      }
      return true;
    }

    @Override
    public boolean remove(int id) {
      if (!set.get(id)) return false;
      set.clear(id);
      return true;
    }

    @Override
    public IntIterator intIterator() {
      return new BitSetIterator(set);
    }
  }

  // when file ids are large, then we use partitioned bit set
  private static class PartitionedBitSetStorage implements SetStorage {
    private final Int2ObjectMap<BitSet> map = new Int2ObjectOpenHashMap<>();

    @Override
    public int size() {
      int result = 0;
      for (Int2ObjectMap.Entry<BitSet> entry : map.int2ObjectEntrySet()) {
        result += entry.getValue().cardinality();
      }
      return result;
    }

    @Override
    public boolean containsId(int id) {
      BitSet partition = map.get(id / BIT_SET_PARTITION_SIZE);
      return partition != null && partition.get(id % BIT_SET_PARTITION_SIZE);
    }

    @Override
    public boolean add(int id) {
      BitSet partition = map.computeIfAbsent(
        id / BIT_SET_PARTITION_SIZE,
        k -> new BitSet(BIT_SET_PARTITION_SIZE - 1)
      );
      if (partition.get(id % BIT_SET_PARTITION_SIZE)) return false;
      partition.set(id % BIT_SET_PARTITION_SIZE);
      return true;
    }

    @Override
    public boolean remove(int id) {
      BitSet partition = map.get(id / BIT_SET_PARTITION_SIZE);
      if (partition == null || !partition.get(id % BIT_SET_PARTITION_SIZE)) return false;
      partition.clear(id % BIT_SET_PARTITION_SIZE);
      if (partition.cardinality() == 0) {
        map.remove(id / BIT_SET_PARTITION_SIZE);
      }
      return true;
    }

    @Override
    public int[] toIntArray() {
      return map.int2ObjectEntrySet().stream().flatMapToInt(entry -> {
        int partitionOffset = entry.getIntKey() * BIT_SET_PARTITION_SIZE;
        return entry.getValue().stream().map(id -> partitionOffset + id);
      }).toArray();
    }

    @Override
    public IntIterator intIterator() {
      return new PartitionedBitSetIterator(map);
    }
  }

  private static class BitSetIterator implements IntIterator {
    private final @NotNull BitSet myBitSet;
    private int currentBit = -1;
    private int toRemoveBit = -1;
    private Boolean hasNext;

    private BitSetIterator(@NotNull BitSet set) {
      myBitSet = set;
    }

    @Override
    public boolean hasNext() {
      findNext();
      return hasNext;
    }

    @Override
    public int nextInt() {
      findNext();
      if (!hasNext) {
        throw new NoSuchElementException();
      }
      hasNext = null;
      toRemoveBit = currentBit;
      return currentBit;
    }

    @Override
    public void remove() {
      if (toRemoveBit >= 0) {
        myBitSet.clear(toRemoveBit);
        toRemoveBit = -1;
      }
    }

    private void findNext() {
      if (hasNext == null) {
        currentBit = myBitSet.nextSetBit(this.currentBit + 1);
        hasNext = currentBit != -1;
      }
    }
  }

  private static class PartitionedBitSetIterator implements IntIterator {

    private int next;
    private boolean canRemove;
    private Boolean hasNext;
    private BitSetIterator idIterator;
    private Int2ObjectMap.Entry<BitSet> curEntry;
    private final Iterator<Int2ObjectMap.Entry<BitSet>> entryIterator;

    PartitionedBitSetIterator(Int2ObjectMap<BitSet> map) {
      entryIterator = map.int2ObjectEntrySet().iterator();
    }

    @Override
    public boolean hasNext() {
      canRemove = false;
      findNext();
      return hasNext;
    }

    private void findNext() {
      if (hasNext == null) {
        hasNext = false;
        int id = -1;
        if (curEntry != null && idIterator != null && idIterator.hasNext()) {
          id = idIterator.nextInt();
        }
        else {
          while (entryIterator.hasNext()) {
            curEntry = entryIterator.next();
            idIterator = new BitSetIterator(curEntry.getValue());
            if (idIterator.hasNext()) {
              id = idIterator.nextInt();
              break;
            }
          }
        }
        if (id >= 0) {
          int offset = curEntry.getIntKey() * BIT_SET_PARTITION_SIZE;
          next = id + offset;
          hasNext = true;
        }
      }
    }

    @Override
    public int nextInt() {
      findNext();

      if (!hasNext) {
        throw new NoSuchElementException();
      }

      canRemove = true;
      int result = next;
      hasNext = null;
      return result;
    }

    @Override
    public void remove() {
      if (!canRemove)
        throw new IllegalStateException("Cannot remove element using PartitionedBitSetIterator after a call to hasNext().");
      idIterator.remove();
      if (curEntry.getValue().isEmpty()) {
        entryIterator.remove();
      }
    }
  }
}
