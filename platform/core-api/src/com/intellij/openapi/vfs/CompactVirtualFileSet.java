// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Set of VirtualFiles optimized for compact storage of very large number of files.
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
public final class CompactVirtualFileSet extends AbstractSet<VirtualFile> implements VirtualFileSet {
  static final int BIT_SET_LIMIT = 1000;
  static final int INT_SET_LIMIT = 10;

  // all non-VirtualFileWithId files and first several files are stored here
  private final Set<VirtualFile> weirdFiles = new HashSet<>();
  // when file set become large, they stored as id-set here
  private StrippedIntOpenHashSet idSet;
  // when file set become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private BitSet fileIds;
  private boolean frozen;

  /**
   * @deprecated Use {@link VfsUtilCore#createCompactVirtualFileSet()} instead
   */
  @Deprecated
  @ApiStatus.Internal
  public CompactVirtualFileSet() {
  }

  /**
   * @deprecated Use {@link VfsUtilCore#createCompactVirtualFileSet(Collection)} instead
   */
  @Deprecated
  @ApiStatus.Internal
  CompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    addAll(files);
  }

  //TODO hide it
  /**
   * @deprecated Use {@link VfsUtilCore#createCompactVirtualFileSet()} instead
   */
  @Deprecated
  @ApiStatus.Internal
  public CompactVirtualFileSet(int @NotNull [] fileIds) {
    idSet = new StrippedIntOpenHashSet();
    for (int id : fileIds) {
      idSet.add(id);
    }
    if (idSet.size() > BIT_SET_LIMIT) {
      convertToBitSet();
    }
  }

  //TODO hide it
  @ApiStatus.Internal
  public boolean containsId(int fileId) {
    if (idSet != null) {
      return idSet.contains(fileId);
    }
    if (fileIds != null) {
      return fileIds.get(fileId);
    }
    for (VirtualFile file : weirdFiles) {
      if (file instanceof VirtualFileWithId && ((VirtualFileWithId)file).getId() == fileId) {
        return true;
      }
    }
    return false;
  }

  //TODO hide it
  @ApiStatus.Internal
  public int @NotNull [] onlyInternalFileIds() {
    if (idSet != null) {
      return idSet.toArray();
    }
    if (fileIds != null) {
      return fileIds.stream().toArray();
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
      BitSet ids = fileIds;
      int id = ((VirtualFileWithId)file).getId();
      if (ids != null) {
        return ids.get(id);
      }
      StrippedIntOpenHashSet idSet = this.idSet;
      if (idSet != null) {
        return idSet.contains(id);
      }
    }
    return weirdFiles.contains(file);
  }

  @Override
  public boolean add(@NotNull VirtualFile file) {
    if (frozen) {
      throw new UnsupportedOperationException();
    }
    boolean added;
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      BitSet ids = fileIds;
      StrippedIntOpenHashSet idSet = this.idSet;
      if (ids != null) {
        added = !ids.get(id);
        ids.set(id);
      }
      else if (idSet != null) {
        added = idSet.add(id);
        if (idSet.size() > BIT_SET_LIMIT) {
          convertToBitSet();
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
    StrippedIntOpenHashSet idSet = new StrippedIntOpenHashSet(weirdFiles.size());
    for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile wf = iterator.next();
      if (wf instanceof VirtualFileWithId) {
        idSet.add(((VirtualFileWithId)wf).getId());
        iterator.remove();
      }
    }
    this.idSet = idSet;
  }

  private void convertToBitSet() {
    fileIds = new BitSet();
    StrippedIntOpenHashSet.SetIterator iterator = idSet.iterator();
    while (iterator.hasNext()) {
      fileIds.set(iterator.nextInt());
    }
    this.idSet = null;
  }

  @Override
  public boolean remove(Object o) {
    if (frozen) {
      throw new IllegalStateException();
    }

    if (weirdFiles.remove(o)) {
      return true;
    }
    if (!(o instanceof VirtualFileWithId)) {
      return false;
    }
    int fileId = ((VirtualFileWithId)o).getId();
    if (fileIds != null && fileIds.get(fileId)) {
      fileIds.clear(fileId);
      return true;
    }
    if (idSet != null && idSet.remove(fileId)) {
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    weirdFiles.clear();
    idSet = null;
    fileIds = null;
  }


  /**
   * Make unmodifiable
   */
  @Override
  public void freeze() {
    frozen = true;
  }

  @Override
  public Set<VirtualFile> freezed() {
    freeze();
    return this;
  }

  @Override
  public boolean process(@NotNull Processor<? super VirtualFile> processor) {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    BitSet ids = fileIds;
    if (ids != null) {
      for (int id = ids.nextSetBit(0); id < ids.size(); id = ids.nextSetBit(id + 1)) {
        if (id < 0) break;
        VirtualFile file = virtualFileManager.findFileById(id);
        if (file != null && !processor.process(file)) return false;
      }
    }
    StrippedIntOpenHashSet idSet = this.idSet;
    if (idSet != null) {
      StrippedIntOpenHashSet.SetIterator iterator = idSet.iterator();
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
    BitSet ids = fileIds;
    StrippedIntOpenHashSet idSet = this.idSet;
    return (ids == null ? 0 : ids.cardinality()) + (idSet == null ? 0 : idSet.size()) + weirdFiles.size();
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    if (frozen) {
      throw new IllegalStateException();
    }
    if (c instanceof CompactVirtualFileSet) {
      boolean modified = false;
      StrippedIntOpenHashSet specifiedIdSet = ((CompactVirtualFileSet)c).idSet;
      BitSet specifiedFileIds = ((CompactVirtualFileSet)c).fileIds;
      Set<VirtualFile> specifiedWeirdFiles = ((CompactVirtualFileSet)c).weirdFiles;

      if (idSet != null) {
        IntArrayList toRemove = new IntArrayList();
        StrippedIntOpenHashSet.SetIterator iterator = idSet.iterator();
        while (iterator.hasNext()) {
          int id = iterator.nextInt();
          if (!contains(id, specifiedIdSet, specifiedFileIds, specifiedWeirdFiles)) {
            toRemove.add(id);
            modified = true;
          }
        }
        for (int i : toRemove.toArray()) {
          idSet.remove(i);
        }
      }

      if (fileIds != null) {
        for (int id : fileIds.stream().toArray()) {
          if (!contains(id, specifiedIdSet, specifiedFileIds, specifiedWeirdFiles)) {
            fileIds.set(id, false);
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

  @Override
  public boolean addAll(@NotNull Collection<? extends VirtualFile> c) {
    if (frozen) {
      throw new IllegalStateException();
    }
    if (c instanceof CompactVirtualFileSet) {
      boolean modified = false;
      CompactVirtualFileSet setToAdd = (CompactVirtualFileSet)c;
      for (VirtualFile file : setToAdd.weirdFiles) {
        if (add(file)) {
          modified = true;
        }
      }

      IntStream toAdd;
      if (setToAdd.idSet != null) {
        toAdd = Arrays.stream(setToAdd.idSet.toArray());
      }
      else if (setToAdd.fileIds != null) {
        toAdd = setToAdd.fileIds.stream();
      }
      else {
        return modified;
      }

      PrimitiveIterator.OfInt iterator = toAdd.iterator();
      while (iterator.hasNext()) {
        int id = iterator.nextInt();

        if (fileIds == null && idSet == null) {
          convertToIntSet();
        }

        if (fileIds != null) {
          modified = !fileIds.get(id);
          fileIds.set(id);
        }
        else if (idSet != null) {
          modified = idSet.add(id);
          if (idSet.size() > BIT_SET_LIMIT) {
            convertToBitSet();
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

  @NotNull
  @Override
  public Iterator<VirtualFile> iterator() {
    BitSet ids = fileIds;
    StrippedIntOpenHashSet idSet = this.idSet;
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Iterator<VirtualFile> fromIdsIterator;
    if (ids == null) {
      fromIdsIterator = Collections.emptyIterator();
    }
    else {
      BitSetIterator idIterator = new BitSetIterator(ids);
      fromIdsIterator = new Iterator<VirtualFile>() {
        @Override
        public boolean hasNext() {
          return idIterator.hasNext();
        }

        @Override
        public VirtualFile next() {
          ProgressManager.checkCanceled();
          return virtualFileManager.findFileById(idIterator.next());
        }

        @Override
        public void remove() {
          idIterator.remove();
        }
      };
    }

    Iterator<? extends VirtualFile> totalIterator;
    if (idSet == null) {
      totalIterator = ContainerUtil.concatIterators(fromIdsIterator, weirdFiles.iterator());
    }
    else {
      Iterator<VirtualFile> idSetIterator = new Iterator<VirtualFile>() {
        final StrippedIntOpenHashSet.SetIterator iterator = idSet.iterator();

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
        myBitSet.set(currentBit, false);
      }
    }

    private void findNext() {
      if (hasNext == null) {
        currentBit = myBitSet.nextSetBit(this.currentBit + 1);
        hasNext = currentBit != -1;
      }
    }
  }

  private static boolean contains(int id, @Nullable StrippedIntOpenHashSet idSet, @Nullable BitSet fileIds, @NotNull Set<? extends VirtualFile> weirdFiles) {
    if (idSet != null && idSet.contains(id)) return true;
    if (fileIds != null && fileIds.get(id)) return true;
    for (VirtualFile file : weirdFiles) {
      if (file instanceof VirtualFileWithId && ((VirtualFileWithId)file).getId() == id) {
        return true;
      }
    }
    return false;
  }
}
