// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class FileElementTypeModificationCounterImpl implements StubIndexEx.FileElementTypeModificationCounter {
  private final ConcurrentHashMap<Class<? extends IFileElementType>, Integer> myFileElementTypeModCount = new ConcurrentHashMap<>();
  private final AtomicInteger myGlobalShift = new AtomicInteger(0);
  private final FileBasedIndexImpl myFileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();

  @Override
  public int incModCount(@NotNull Class<? extends IFileElementType> fileElementTypeClass) {
    return myFileElementTypeModCount.compute(fileElementTypeClass, (__, value) -> {
      if (value == null) {
        return 1 - myGlobalShift.get();
      }
      return value + 1;
    }) + myGlobalShift.get();
  }

  @Override
  public int getModCount(@NotNull Class<? extends IFileElementType> fileElementTypeClass) {
    if (StubIndexImpl.FILE_ELEMENT_TYPE_CHANGE_TRACKING_SOURCE == StubIndexImpl.FileElementTypeChangeTrackingSource.ChangedFilesCollector) {
      myFileBasedIndex.getChangedFilesCollector().processFilesToUpdateInReadAction();
    }
    return myFileElementTypeModCount.compute(fileElementTypeClass, (__, value) -> {
      if (value == null) {
        return -myGlobalShift.get();
      }
      return value;
    }) + myGlobalShift.get();
  }

  @Override
  public void incGlobalModCount() {
    myGlobalShift.incrementAndGet();
  }
}

