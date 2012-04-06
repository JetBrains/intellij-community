/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class CoreLocalFileSystemWithId extends DeprecatedVirtualFileSystem {
  @NotNull private final StripedLockIntObjectConcurrentHashMap<CoreLocalVirtualFileWithId> myIdToFileCache = new StripedLockIntObjectConcurrentHashMap<CoreLocalVirtualFileWithId>();
  @NotNull private final StripedLockConcurrentHashMap<String, CoreLocalVirtualFileWithId> myPathToFileCache = new StripedLockConcurrentHashMap<String, CoreLocalVirtualFileWithId>();

  @NotNull
  @Override
  public String getProtocol() {
    return "file";
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    CoreLocalVirtualFileWithId coreLocalVirtualFileWithId = myPathToFileCache.get(path);
    if(coreLocalVirtualFileWithId != null)
      return coreLocalVirtualFileWithId;

    File ioFile = new File(path);
    if (ioFile.exists()) {
      coreLocalVirtualFileWithId = new CoreLocalVirtualFileWithId(this, ioFile);
      myIdToFileCache.put(coreLocalVirtualFileWithId.getId(), coreLocalVirtualFileWithId);
      myPathToFileCache.put(path, coreLocalVirtualFileWithId);

      return coreLocalVirtualFileWithId;
    }
    return null;
  }

  @Override
  public void refresh(boolean asynchronous) {
    //call FileBasedIndexProjectHandlerJavaComponent RefreshCacheUpdater
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    throw new UnsupportedOperationException("deleteFile() not supported");
  }

  @Override
  protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException("move() not supported");
  }

  @Override
  protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new UnsupportedOperationException("renameFile() not supported");
  }

  @Override
  protected VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new UnsupportedOperationException("createChildFile() not supported");
  }

  @NotNull
  @Override
  protected VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new UnsupportedOperationException("createChildDirectory() not supported");
  }

  @Override
  protected VirtualFile copyFile(Object requestor,
                                 @NotNull VirtualFile virtualFile,
                                 @NotNull VirtualFile newParent,
                                 @NotNull String copyName) throws IOException {
    throw new UnsupportedOperationException("copyFile() not supported");
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  public VirtualFile findFileById(int id) {
    return myIdToFileCache.get(id);
  }
}
