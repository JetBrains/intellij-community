// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.containers.IdBitSet;
import com.intellij.util.indexing.containers.IntIdsIterator;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.*;

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
  /** Max weirdFiles.size to convert storage to {@link IntSetStorage} */
  @VisibleForTesting
  public static final int INT_SET_LIMIT = 10;
  /**
   * Max storage.size to convert {@link IntSetStorage} impl to either {@link IdBitSetStorage} or {@link PartitionedBitSetStorage}
   * (depending on ids range)
   */
  @VisibleForTesting
  public static final int BIT_SET_LIMIT = 1000;
  /** max fileId range covered by {@link IdBitSetStorage}, to convert it to {@link PartitionedBitSetStorage} */
  @VisibleForTesting
  public static final int PARTITION_BIT_SET_LIMIT = 20000;
  /**
   * If {@link IdBitSetStorage} covers id range more than PARTITION_BIT_SET_LIMIT, and less than this % of {@link IdBitSetStorage}
   * bits are set -> convert it to {@link PartitionedBitSetStorage}
   */
  private static final int PARTITION_BIT_SET_THRESHOLD = 25; // 25% of bits are set
  /** Size of (=id range covered by) individual partition in {@link PartitionedBitSetStorage} */
  private static final int PARTITION_BIT_SET_PARTITION_SIZE = 2048; // =256 bytes per partition

  /**
   * First several (=less than INT_SET_LIMIT) files are stored here, then all non-{@link VirtualFileWithId} files are stored
   * here afterward.
   * If storage == null, then this set _could_ contain both {@link VirtualFileWithId} and non-{@link VirtualFileWithId} files.
   * If storage!=null => all {@link VirtualFileWithId} are stored in the storage, as fileIds, and only non-{@link VirtualFileWithId}
   * files are stored in this set.
   */
  private final Set<VirtualFile> weirdFiles = new HashSet<>();
  /**
   * file id storage; an actual implementation is switched in runtime to accommodate actual ids set memory-efficiently.
   * Could be null if there are very few files in the set (=less than INT_SET_LIMIT), so all of them fit into {@link #weirdFiles}.
   * <p>
   * When a file set become large (>INT_SET_LIMIT), fileIds are stored using {@link IntSetStorage}
   * When a file set become very big (>BIT_SET_LIMIT, e.g. whole project files AnalysisScope) a {@link IdBitSetStorage} is used with a
   * bit-mask of their ids
   * When file ids start to spread out too much, i.e.
   * {@code max - min > PARTITION_BIT_SET_LIMIT && 100*size/(max-min) < PARTITION_BIT_SET_THRESHOLD}
   * => a {@link PartitionedBitSetStorage} is used
   */
  //TODO RC: current implementation only able to adapt to set growing -- but set could also shrink (=remove() method _is_ implemented),
  //         so we also need 'downward' adaptation
  private @Nullable SetStorage storage;
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
  public CompactVirtualFileSet(@NotNull IntSet fileIds) {
    storage = new IntSetStorage(fileIds);
    if (storage.size() > BIT_SET_LIMIT) {
      convertToBitSet();
    }
  }

  @Override
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

    if (!(file instanceof VirtualFileWithId)) {
      return weirdFiles.add(file);
    }

    if (storage == null) {
      boolean added = weirdFiles.add(file);
      if (weirdFiles.size() > INT_SET_LIMIT) {
        convertToIntSet();
      }
      return added;
    }

    int id = ((VirtualFileWithId)file).getId();
    return addToStorageWithUpgradeCheck(id);
  }

  private boolean addToStorageWithUpgradeCheck(int fileId) {
    //TODO RC: current design is complicated.
    //         Simpler approach is to add the fileId directly to the storage, but each storage impl throws an exception
    //         (~StorageOverflowException) if it can't accommodate this fileId -> the exception is caught and another
    //         storage impl is chosen then.
    if (storage instanceof IntSetStorage
        && storage.shouldUpgradeBeforeAdd(fileId)) {
      convertToBitSet();
    }
    if (storage instanceof IdBitSetStorage
        && storage.shouldUpgradeBeforeAdd(fileId)) {
      convertToPartitionedBitSet();
    }
    return storage.add(fileId);
  }

  private void convertToIntSet() {
    storage = new IntSetStorage();
    for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile wf = iterator.next();
      if (wf instanceof VirtualFileWithId) {
        addToStorageWithUpgradeCheck(((VirtualFileWithId)wf).getId());
        iterator.remove();
      }
    }
  }

  private void convertToBitSet() {
    IntIterator iterator = storage.intIterator();
    storage = new IdBitSetStorage();
    while (iterator.hasNext()) {
      addToStorageWithUpgradeCheck(iterator.nextInt());
    }
  }

  private void convertToPartitionedBitSet() {
    IntIterator iterator = storage.intIterator();
    storage = new PartitionedBitSetStorage();
    while (iterator.hasNext()) {
      //should be addToStorageWithUpgradeCheck(), but there is nothing to upgrade PartitionedBitSetStorage to
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
  public @NotNull @Unmodifiable Set<VirtualFile> freezed() {
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
        addToStorageWithUpgradeCheck(id);
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

    default int[] toIntArray() {
      int[] result = new int[size()];
      IntIterator iterator = intIterator();
      for (int i = 0; i < result.length; i++) {
        result[i] = iterator.nextInt();
      }
      return result;
    }

    boolean add(int id);

    IntIterator intIterator();

    boolean remove(int id);

    boolean shouldUpgradeBeforeAdd(int id);
  }

  private static class IntSetStorage implements SetStorage {
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
    public boolean shouldUpgradeBeforeAdd(int id) {
      return set.size() >= BIT_SET_LIMIT;
    }

    @Override
    public boolean add(int id) {
      return set.add(id);
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

  private static class IdBitSetStorage implements SetStorage {
    private final IdBitSet set = new IdBitSet(INT_SET_LIMIT);

    @Override
    public int size() {
      return set.size();
    }

    @Override
    public boolean containsId(int id) {
      return set.contains(id);
    }

    @Override
    public boolean shouldUpgradeBeforeAdd(int id) {
      int size = set.size();
      if (size == 0) return false;

      int setMin = set.getMin();
      int setMax = set.getMax();
      if (setMin <= id && id <= setMax) return false;

      int min = Math.min(setMin, id);
      int max = Math.max(setMax, id);

      if (max - min < PARTITION_BIT_SET_LIMIT) {
        return false;
      }
      return 100 * (size + 1) / (max - min) < PARTITION_BIT_SET_THRESHOLD;
    }

    @Override
    public boolean add(int id) {
      return set.add(id);
    }

    @Override
    public boolean remove(int id) {
      return set.remove(id);
    }

    @Override
    public IntIterator intIterator() {
      return new IdBitSetIterator(set);
    }
  }

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
      BitSet partition = map.get(id / PARTITION_BIT_SET_PARTITION_SIZE);
      return partition != null && partition.get(id % PARTITION_BIT_SET_PARTITION_SIZE);
    }

    @Override
    public boolean shouldUpgradeBeforeAdd(int id) {
      return false;
    }

    @Override
    public boolean add(int id) {
      BitSet partition = map.computeIfAbsent(
        id / PARTITION_BIT_SET_PARTITION_SIZE,
        k -> new BitSet(PARTITION_BIT_SET_PARTITION_SIZE - 1)
      );
      if (partition.get(id % PARTITION_BIT_SET_PARTITION_SIZE)) return false;
      partition.set(id % PARTITION_BIT_SET_PARTITION_SIZE);
      return true;
    }

    @Override
    public boolean remove(int id) {
      BitSet partition = map.get(id / PARTITION_BIT_SET_PARTITION_SIZE);
      if (partition == null || !partition.get(id % PARTITION_BIT_SET_PARTITION_SIZE)) return false;
      partition.clear(id % PARTITION_BIT_SET_PARTITION_SIZE);
      if (partition.cardinality() == 0) {
        map.remove(id / PARTITION_BIT_SET_PARTITION_SIZE);
      }
      return true;
    }

    @Override
    public IntIterator intIterator() {
      return new PartitionedBitSetIterator(map);
    }
  }

  private static class IdBitSetIterator implements IntIterator {
    private final IdBitSet set;
    private final @NotNull IntIdsIterator iterator;
    private int toRemove;

    private IdBitSetIterator(@NotNull IdBitSet set) {
      this.set = set;
      iterator = set.intIterator();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public int nextInt() {
      toRemove = iterator.next();
      return toRemove;
    }

    @Override
    public void remove() {
      set.remove(toRemove);
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
          int offset = curEntry.getIntKey() * PARTITION_BIT_SET_PARTITION_SIZE;
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
      if (!canRemove) {
        throw new IllegalStateException("Cannot remove element using PartitionedBitSetIterator after a call to hasNext().");
      }
      idIterator.remove();
      if (curEntry.getValue().isEmpty()) {
        entryIterator.remove();
      }
    }
  }
}
