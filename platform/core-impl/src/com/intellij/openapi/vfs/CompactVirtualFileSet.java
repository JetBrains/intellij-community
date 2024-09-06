// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
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
  static final int BIT_SET_LIMIT = 1000;
  static final int INT_SET_LIMIT = 10;
  static final int BIT_SET_PARTITION = 8192;

  // all non-VirtualFileWithId files and first several files (up to INT_SET_LIMIT) are stored here
  private final Set<VirtualFile> weirdFiles = new HashSet<>();
  // when file set become large (>BIT_SET_LIMIT), they stored as id-set here
  private IntSet idSet;
  // when file set become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private Int2ObjectMap<BitSet> fileIdPartitionMap;
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
    idSet = fileIds;
    if (idSet.size() > BIT_SET_LIMIT) {
      convertToPartitionedBitSet();
    }
  }

  @Override
  @ApiStatus.Internal
  public boolean containsId(int fileId) {
    IntSet idSet = this.idSet;
    if (idSet != null) {
      return idSet.contains(fileId);
    }
    if (fileIdPartitionMap != null) {
      BitSet partition = fileIdPartitionMap.get(fileId / BIT_SET_PARTITION);
      return partition != null && partition.get(fileId % BIT_SET_PARTITION);
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
    IntSet idSet = this.idSet;
    if (idSet != null) {
      return idSet.toIntArray();
    }
    if (fileIdPartitionMap != null) {
      return fileIdPartitionMap.int2ObjectEntrySet().stream().flatMapToInt(entry -> {
        int partitionOffset = entry.getIntKey() * BIT_SET_PARTITION;
        return entry.getValue().stream().map(id -> partitionOffset + id);
      }).toArray();
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
      Int2ObjectMap<BitSet> ids = fileIdPartitionMap;
      if (ids != null) {
        BitSet partition = ids.get(id / BIT_SET_PARTITION);
        return partition != null && partition.get(id % BIT_SET_PARTITION);
      }
      IntSet idSet = this.idSet;
      if (idSet != null) {
        return idSet.contains(id);
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
      Int2ObjectMap<BitSet> ids = fileIdPartitionMap;
      IntSet idSet = this.idSet;
      if (ids != null) {
        BitSet partition = ids.computeIfAbsent(
          id / BIT_SET_PARTITION,
          k -> new BitSet(BIT_SET_PARTITION - 1)
        );
        added = !partition.get(id % BIT_SET_PARTITION);
        if (added) {
          partition.set(id % BIT_SET_PARTITION);
        }
      }
      else if (idSet != null) {
        added = idSet.add(id);
        if (idSet.size() > BIT_SET_LIMIT) {
          convertToPartitionedBitSet();
        }
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
    IntSet idSet = new IntOpenHashSet(weirdFiles.size());
    for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile wf = iterator.next();
      if (wf instanceof VirtualFileWithId) {
        idSet.add(((VirtualFileWithId)wf).getId());
        iterator.remove();
      }
    }
    this.idSet = idSet;
  }

  private void convertToPartitionedBitSet() {
    fileIdPartitionMap = new Int2ObjectOpenHashMap<>();
    IntIterator iterator = idSet.intIterator();
    while (iterator.hasNext()) {
      int id = iterator.nextInt();
      BitSet partition = fileIdPartitionMap.computeIfAbsent(
        id / BIT_SET_PARTITION,
        k -> new BitSet(BIT_SET_PARTITION - 1)
      );
      partition.set(id % BIT_SET_PARTITION);
    }
    this.idSet = null;
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
    if (fileIdPartitionMap != null) {
      BitSet partition = fileIdPartitionMap.get(fileId / BIT_SET_PARTITION);
      if (partition != null && partition.get(fileId % BIT_SET_PARTITION)) {
        partition.clear(fileId % BIT_SET_PARTITION);
        if (partition.cardinality() == 0) {
          fileIdPartitionMap.remove(fileId / BIT_SET_PARTITION);
        }
        return true;
      }
    }
    if (idSet != null && idSet.remove(fileId)) {
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    assertNotFrozen();
    weirdFiles.clear();
    idSet = null;
    fileIdPartitionMap = null;
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
    Int2ObjectMap<BitSet> ids = fileIdPartitionMap;
    if (ids != null) {
      for (Int2ObjectMap.Entry<BitSet> entry : ids.int2ObjectEntrySet()) {
        int partitionOffset = entry.getIntKey() * BIT_SET_PARTITION;
        BitSet partition = entry.getValue();
        BitSetIterator iterator = new BitSetIterator(partition);
        while (iterator.hasNext()) {
          int id = iterator.next();
          VirtualFile file = virtualFileManager.findFileById(partitionOffset + id);
          if (file != null && !processor.process(file)) return false;
        }
      }
    }
    IntSet idSet = this.idSet;
    if (idSet != null) {
      IntIterator iterator = idSet.iterator();
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
    int result = 0;
    Int2ObjectMap<BitSet> ids = fileIdPartitionMap;
    if (ids != null) {
      for (Int2ObjectMap.Entry<BitSet> entry : ids.int2ObjectEntrySet()) {
        result += entry.getValue().cardinality();
      }
    }
    else if (idSet != null) {
      result += idSet.size();
    }
    return result + weirdFiles.size();
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    assertNotFrozen();
    if (c instanceof CompactVirtualFileSet) {
      CompactVirtualFileSet compactVirtualFileSet = (CompactVirtualFileSet)c;
      boolean modified = false;

      if (idSet != null) {
        IntIterator iterator = idSet.intIterator();
        while (iterator.hasNext()) {
          int id = iterator.nextInt();
          if (!compactVirtualFileSet.containsId(id)) {
            iterator.remove();
            modified = true;
          }
        }
      }

      if (fileIdPartitionMap != null) {
        ObjectIterator<Int2ObjectMap.Entry<BitSet>> entryIterator = fileIdPartitionMap.int2ObjectEntrySet().iterator();
        while (entryIterator.hasNext()) {
          Int2ObjectMap.Entry<BitSet> entry = entryIterator.next();
          int offset = entry.getIntKey() * BIT_SET_PARTITION;
          BitSetIterator bitSetIterator = new BitSetIterator(entry.getValue());
          boolean removedAll = true;
          while (bitSetIterator.hasNext()) {
            if (!compactVirtualFileSet.containsId(bitSetIterator.next() + offset)) {
              bitSetIterator.remove();
              modified = true;
            }
            else {
              removedAll = false;
            }
          }
          if (removedAll) {
            entryIterator.remove();
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

      IntIterator toAdd;
      if (setToAdd.idSet != null) {
        toAdd = setToAdd.idSet.intIterator();
      }
      else if (setToAdd.fileIdPartitionMap != null) {
        toAdd = IntIterators.asIntIterator(
          setToAdd.fileIdPartitionMap.int2ObjectEntrySet().stream().flatMapToInt(entry -> {
            int offset = entry.getIntKey() * BIT_SET_PARTITION;
            return entry.getValue().stream().map(it -> it + offset);
          }).iterator()
        );
      }
      else {
        return modified;
      }

      while (toAdd.hasNext()) {
        int id = toAdd.nextInt();

        if (fileIdPartitionMap == null && idSet == null) {
          convertToIntSet();
        }

        if (fileIdPartitionMap != null) {
          BitSet partition = fileIdPartitionMap.computeIfAbsent(
            id / BIT_SET_PARTITION,
            k -> new BitSet(BIT_SET_PARTITION - 1)
          );
          if (!partition.get(id % BIT_SET_PARTITION)) {
            modified = true;
            partition.set(id % BIT_SET_PARTITION);
          }
        }
        else if (idSet != null) {
          modified = idSet.add(id) || modified;
          if (idSet.size() > BIT_SET_LIMIT) {
            convertToPartitionedBitSet();
          }
        }
        else {
          throw new IllegalStateException();
        }
      }

      return modified;
    }
    else {
      return super.addAll(c);
    }
  }

  @Override
  public @NotNull Iterator<VirtualFile> iterator() {
    Int2ObjectMap<BitSet> ids = fileIdPartitionMap;
    IntSet idSet = this.idSet;
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Iterator<VirtualFile> fromIdsIterator;
    if (ids == null || ids.isEmpty()) {
      fromIdsIterator = Collections.emptyIterator();
    }
    else {
      ObjectIterator<Int2ObjectMap.Entry<BitSet>> entryIterator = ids.int2ObjectEntrySet().iterator();
      fromIdsIterator = new Iterator<VirtualFile>() {

        private VirtualFile next;
        private Boolean hasNext;
        private BitSetIterator idIterator;
        private Int2ObjectMap.Entry<BitSet> curEntry;

        @Override
        public boolean hasNext() {
          findNext();
          return hasNext;
        }

        private void findNext() {
          if (hasNext == null) {
            hasNext = false;
            int id = -1;
            if (curEntry != null && idIterator != null && idIterator.hasNext()) {
              id = idIterator.next();
            }
            else {
              while (entryIterator.hasNext()) {
                curEntry = entryIterator.next();
                idIterator = new BitSetIterator(curEntry.getValue());
                if (idIterator.hasNext()) {
                  id = idIterator.next();
                  break;
                }
              }
            }
            if (id >= 0) {
              int offset = curEntry.getIntKey() * BIT_SET_PARTITION;
              next = virtualFileManager.findFileById(id + offset);
              hasNext = true;
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
          idIterator.remove();
          if (curEntry.getValue().isEmpty()) {
            entryIterator.remove();
          }
        }
      };
    }

    Iterator<? extends VirtualFile> totalIterator;
    if (idSet == null) {
      totalIterator = ContainerUtil.concatIterators(fromIdsIterator, weirdFiles.iterator());
    }
    else {
      Iterator<VirtualFile> idSetIterator = new Iterator<VirtualFile>() {
        final IntIterator iterator = idSet.intIterator();

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
      totalIterator = ContainerUtil.concatIterators(fromIdsIterator, idSetIterator, weirdFiles.iterator());
    }

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

  private static class BitSetIterator {
    private final @NotNull BitSet myBitSet;
    private int currentBit = -1;
    private Boolean hasNext;

    private BitSetIterator(@NotNull BitSet set) {
      myBitSet = set;
    }

    public boolean hasNext() {
      findNext();
      return hasNext;
    }

    public int next() {
      findNext();
      if (!hasNext) {
        throw new NoSuchElementException();
      }
      hasNext = null;
      return currentBit;
    }

    // be careful: doesn't follow contract of Iterator#remove()
    public void remove() {
      if (currentBit >= 0) {
        myBitSet.clear(currentBit);
      }
    }

    private void findNext() {
      if (hasNext == null) {
        currentBit = myBitSet.nextSetBit(this.currentBit + 1);
        hasNext = currentBit != -1;
      }
    }
  }
}
