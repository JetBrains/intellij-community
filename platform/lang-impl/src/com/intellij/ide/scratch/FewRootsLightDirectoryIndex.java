// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This is a light version of DirectoryIndexImpl which monitors files in the local file system under a handful of roots,
 * passed via {@code rootSupplier} constructor argument
 */
final class FewRootsLightDirectoryIndex<T> implements VirtualFileEnumeration {
  private final ConcurrentIntObjectMap<T> myInfos = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private volatile boolean isUpToDate;
  private final T myDefValue;
  private final Supplier<? extends Collection<? extends Map.Entry<VirtualFile, T>>> myRootSupplier;
  private final @NotNull Predicate<? super VFileEvent> myShouldResetIndexPredicate;

  FewRootsLightDirectoryIndex(@NotNull Disposable parentDisposable,
                              @NotNull T defValue,
                              @NotNull Predicate<? super VFileEvent> shouldResetIndexPredicate,
                              @NotNull Supplier<? extends Collection<? extends Map.Entry<VirtualFile, T>>> rootSupplier) {
    myDefValue = defValue;
    myRootSupplier = rootSupplier;
    myShouldResetIndexPredicate = shouldResetIndexPredicate;
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        resetIndex();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (myShouldResetIndexPredicate.test(event)) {
            resetIndex();
            break;
          }
        }
      }
    });
  }

  private void ensureUpToDate() {
    if (!isUpToDate) {
      synchronized (this) {
        if (!isUpToDate) {
          recomputeIndex();
          isUpToDate = true;
        }
      }
    }
  }

  void resetIndex() {
    isUpToDate = false;
  }

  private void recomputeIndex() {
    myInfos.clear();
    Collection<? extends Map.Entry<VirtualFile, T>> roots = myRootSupplier.get();
    for (Map.Entry<VirtualFile, T> entry : roots) {
      VirtualFile virtualFile = entry.getKey();
      T value = entry.getValue();
      VfsUtil.iterateChildrenRecursively(virtualFile, null, fileOrDir -> {
        int id = ((VirtualFileWithId)fileOrDir).getId();
        myInfos.put(id, value);
        return true;
      });
    }
  }

  @NotNull T getInfoForFile(@Nullable VirtualFile file) {
    if (!(file instanceof VirtualFileWithId vid) || !file.isValid()) return myDefValue;
    int id = vid.getId();
    ensureUpToDate();
    return ObjectUtils.chooseNotNull(myInfos.get(id), myDefValue);
  }

  @Override
  public boolean contains(int fileId) {
    ensureUpToDate();
    return myInfos.containsKey(fileId);
  }

  @Override
  public int @NotNull [] asArray() {
    ensureUpToDate();
    return myInfos.keys();
  }
}