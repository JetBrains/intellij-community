/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This is a light version of DirectoryIndexImpl
 *
 * @author gregsh
 */
public final class LightDirectoryIndex<T> {
  private final IntObjectMap<T> myInfoCache = ContainerUtil.createConcurrentIntObjectMap();
  private final T myDefValue;
  private final Consumer<LightDirectoryIndex<T>> myInitializer;

  public LightDirectoryIndex(@NotNull Disposable parentDisposable, @NotNull T defValue, @NotNull Consumer<LightDirectoryIndex<T>> initializer) {
    myDefValue = defValue;
    myInitializer = initializer;
    resetIndex();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        resetIndex();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file == null || file.isDirectory()) {
            resetIndex();
            break;
          }
        }
      }
    });
  }

  public void resetIndex() {
    myInfoCache.clear();
    myInitializer.consume(this);
  }

  public void putInfo(@Nullable VirtualFile file, @NotNull T value) {
    if (!(file instanceof VirtualFileWithId)) return;
    cacheInfo(file, value);
  }

  @NotNull
  public T getInfoForFile(@Nullable VirtualFile file) {
    if (!(file instanceof VirtualFileWithId)) return myDefValue;

    VirtualFile dir;
    if (!file.isDirectory()) {
      T info = getCachedInfo(file);
      if (info != null) {
        return info;
      }
      dir = file.getParent();
    }
    else {
      dir = file;
    }

    int count = 0;
    for (VirtualFile root = dir; root != null; root = root.getParent()) {
      if (++count > 1000) {
        throw new IllegalStateException("Possible loop in tree, started at " + dir.getName());
      }
      T info = getCachedInfo(root);
      if (info != null) {
        if (!dir.equals(root)) {
          cacheInfos(dir, root, info);
        }
        return info;
      }
    }

    return cacheInfos(dir, null, myDefValue);
  }

  @NotNull
  private T cacheInfos(VirtualFile dir, @Nullable VirtualFile stopAt, @NotNull T info) {
    while (dir != null) {
      cacheInfo(dir, info);
      if (dir.equals(stopAt)) {
        break;
      }
      dir = dir.getParent();
    }
    return info;
  }

  private void cacheInfo(VirtualFile file, T info) {
    myInfoCache.put(((VirtualFileWithId)file).getId(), info);
  }

  private T getCachedInfo(VirtualFile file) {
    return myInfoCache.get(((VirtualFileWithId)file).getId());
  }

}
