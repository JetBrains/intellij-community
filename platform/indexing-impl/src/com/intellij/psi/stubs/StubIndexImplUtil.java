// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;

class StubIndexImplUtil {
  @NotNull
  static Iterator<VirtualFile> mapIdIterator(@NotNull IntIterator idIterator, @NotNull IntPredicate filter) {
    FileBasedIndexEx fileBasedIndex = (FileBasedIndexEx)FileBasedIndex.getInstance();
    return new Iterator<>() {
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
        while (idIterator.hasNext()) {
          int id = idIterator.nextInt();
          if (!filter.test(id)) {
            continue;
          }
          VirtualFile t = fileBasedIndex.findFileById(id);
          if (t != null) {
            next = t;
            hasNext = true;
            break;
          }
        }
      }

      @Override
      public VirtualFile next() {
        if (hasNext) {
          VirtualFile result = next;
          findNext();
          return result;
        }
        else {
          throw new NoSuchElementException();
        }
      }

      @Override
      public void remove() {
        idIterator.remove();
      }
    };
  }
}
