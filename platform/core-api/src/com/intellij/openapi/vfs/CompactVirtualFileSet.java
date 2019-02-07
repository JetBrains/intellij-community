// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.IntFunction;

/**
 * Set of VirtualFiles optimized for compact storage of very large number of files
 * Remove operations are not supported.
 */
public class CompactVirtualFileSet extends AbstractSet<VirtualFile> {
  // all non-VirtualFileWithId files and first several files are stored here
  private final Set<VirtualFile> weirdFiles = new THashSet<>();
  // when file set become large, they stored as id-set here
  private TIntHashSet idSet;
  // when file set become very big (e.g. whole project files AnalysisScope) the bit-mask of their ids are stored here
  private BitSet fileIds;
  private boolean frozen;

  public CompactVirtualFileSet() {
  }

  public CompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    addAll(files);
  }

  @Override
  public boolean contains(Object file) {
    if (file instanceof VirtualFileWithId) {
      BitSet ids = fileIds;
      int id = getValidId((VirtualFileWithId)file);
      if (ids != null) {
        return ids.get(id);
      }
      TIntHashSet idSet = this.idSet;
      if (idSet != null) {
        return idSet.contains(id);
      }
    }
    return weirdFiles.contains(file);
  }

  private static int getValidId(@NotNull VirtualFileWithId file) {
    return Math.abs(file.getId());
  }

  @Override
  public boolean add(@NotNull VirtualFile file) {
    if (frozen) {
      throw new UnsupportedOperationException();
    }
    boolean added;
    if (file instanceof VirtualFileWithId) {
      int id = getValidId((VirtualFileWithId)file);
      BitSet ids = fileIds;
      TIntHashSet idSet = this.idSet;
      if (ids != null) {
        added = !ids.get(id);
        ids.set(id);
      }
      else if (idSet != null) {
        added = idSet.add(id);
        if (idSet.size() > 1000) {
          fileIds = new BitSet();
          idSet.forEach(i->{ fileIds.set(i); return true; });
          this.idSet = null;
        }
      }
      else {
        added = weirdFiles.add(file);
        if (weirdFiles.size() > 10) {
          this.idSet = idSet = new TIntHashSet(weirdFiles.size());
          for (Iterator<VirtualFile> iterator = weirdFiles.iterator(); iterator.hasNext(); ) {
            VirtualFile wf = iterator.next();
            if (wf instanceof VirtualFileWithId) {
              int i = getValidId((VirtualFileWithId)wf);
              idSet.add(i);
              iterator.remove();
            }
          }
        }
      }
    }
    else {
      added = weirdFiles.add(file);
    }
    return added;
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
    TIntHashSet idSet = this.idSet;
    if (idSet != null && !idSet.forEach(id -> {
      VirtualFile file = virtualFileManager.findFileById(id);
      return file == null || processor.process(file);
    })) {
      return false;
    }

    return ContainerUtil.process(weirdFiles, processor);
  }

  @Override
  public int size() {
    BitSet ids = fileIds;
    TIntHashSet idSet = this.idSet;
    return (ids == null ? 0 : ids.cardinality()) + (idSet == null ? 0 : idSet.size()) + weirdFiles.size();
  }

  @NotNull
  @Override
  public Iterator<VirtualFile> iterator() {
    BitSet ids = fileIds;
    TIntHashSet idSet = this.idSet;
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Iterator<VirtualFile> idsIterator = ids == null ? Collections.emptyIterator() :
                                               ContainerUtil.mapIterator(ids.stream().iterator(), id -> virtualFileManager.findFileById(id));
    Iterator<VirtualFile> idSetIterator = idSet == null ? Collections.emptyIterator() :
                                          mapIterator(idSet.iterator(), id -> virtualFileManager.findFileById(id));
    Iterator<VirtualFile> weirdFileIterator = weirdFiles.iterator();
    return ContainerUtil.filterIterator(ContainerUtil.concatIterators(idsIterator, idSetIterator, weirdFileIterator), Objects::nonNull);
  }

  // todo move to ContainerUtil when ported util to java level 8
  @NotNull
  @Contract(pure=true)
  public static <U> Iterator<U> mapIterator(@NotNull final TIntIterator iterator, @NotNull final IntFunction<? extends U> mapper) {
    return new Iterator<U>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public U next() {
        return mapper.apply(iterator.next());
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

}
