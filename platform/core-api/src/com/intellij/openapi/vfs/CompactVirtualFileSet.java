// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Set of VirtualFiles optimized for compact storage of very large number of files
 * Supports optimized {@link Collection#add(Object)} and {@link Collection#addAll(Collection)}
 * without materialization of all containing files.
 * Remove operations are not supported.
 * NOT thread-safe.
 */
public final class CompactVirtualFileSet extends AbstractSet<VirtualFile> {
  static final int BIT_SET_LIMIT = 1000;
  static final int INT_SET_LIMIT = 10;

  // all non-VirtualFileWithId files and first several files are stored here
  private final Set<VirtualFile> weirdFiles = new HashSet<>();
  // when file set become large, they stored as id-set here
  private IntSet idSet;
  // when file set become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private BitSet fileIds;
  private boolean frozen;

  public CompactVirtualFileSet() {
  }

  public CompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    addAll(files);
  }

  //TODO hide it
  @ApiStatus.Internal
  public CompactVirtualFileSet(@NotNull IntSet fileIds) {
    idSet = fileIds;
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
      return idSet.toIntArray();
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
      IntSet idSet = this.idSet;
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
      IntSet idSet = this.idSet;
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
    IntSet idSet;
    idSet = new IntOpenHashSet(weirdFiles.size());
    this.idSet = idSet;
    for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile wf = iterator.next();
      if (wf instanceof VirtualFileWithId) {
        idSet.add(((VirtualFileWithId)wf).getId());
        iterator.remove();
      }
    }
  }

  private void convertToBitSet() {
    fileIds = new BitSet();
    IntIterator iterator = idSet.iterator();
    while (iterator.hasNext()) {
      fileIds.set(iterator.nextInt());
    }
    this.idSet = null;
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }


  /**
   * Make unmodifiable
   */
  public void freeze() {
    frozen = true;
  }

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
    BitSet ids = fileIds;
    IntSet idSet = this.idSet;
    return (ids == null ? 0 : ids.cardinality()) + (idSet == null ? 0 : idSet.size()) + weirdFiles.size();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    if (frozen) {
      throw new IllegalStateException();
    }
    if (c instanceof CompactVirtualFileSet) {
      boolean modified = false;
      IntSet specifiedIdSet = ((CompactVirtualFileSet)c).idSet;
      BitSet specifiedFileIds = ((CompactVirtualFileSet)c).fileIds;
      Set<VirtualFile> specifiedWeirdFiles = ((CompactVirtualFileSet)c).weirdFiles;

      if (idSet != null) {
        IntIterator iterator = idSet.intIterator();
        while (iterator.hasNext()) {
          int id = iterator.nextInt();
          if (!contains(id, specifiedIdSet, specifiedFileIds, specifiedWeirdFiles)) {
            iterator.remove();
            modified = true;
          }
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
  public boolean addAll(Collection<? extends VirtualFile> c) {
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
        toAdd = setToAdd.idSet.intStream();
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
    IntSet idSet = this.idSet;
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Iterator<VirtualFile> idsIterator;
    if (ids == null) {
      idsIterator = Collections.emptyIterator();
    }
    else {
      idsIterator = ids.stream()
        .mapToObj(id -> {
          ProgressManager.checkCanceled();
          return virtualFileManager.findFileById(id);
        })
        .iterator();
    }

    Iterator<? extends VirtualFile> totalIterator;
    if (idSet == null) {
      totalIterator = ContainerUtil.concatIterators(idsIterator, weirdFiles.iterator());
    }
    else {
      IntIterator iterator = idSet.iterator();
      Iterator<VirtualFile> idSetIterator = new Iterator<VirtualFile>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public VirtualFile next() {
          int id = iterator.nextInt();
          ProgressManager.checkCanceled();
          return virtualFileManager.findFileById(id);
        }

        @Override
        public void remove() {
          iterator.remove();
        }
      };
      totalIterator = ContainerUtil.concatIterators(idsIterator, idSetIterator, weirdFiles.iterator());
    }

    return new Iterator<VirtualFile>() {
      VirtualFile next;
      boolean hasNext;

      {
        findNext();
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      private void findNext() {
        hasNext = false;
        while (totalIterator.hasNext()) {
          VirtualFile t = totalIterator.next();
          if (t != null) {
            next = t;
            hasNext = true;
            break;
          }
        }
      }

      @Override
      public VirtualFile next() {
        if (!hasNext) {
          throw new NoSuchElementException();
        }

        VirtualFile result = next;
        findNext();
        return result;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static boolean contains(int id, @Nullable IntSet idSet, @Nullable BitSet fileIds, @NotNull Set<VirtualFile> weirdFiles) {
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
