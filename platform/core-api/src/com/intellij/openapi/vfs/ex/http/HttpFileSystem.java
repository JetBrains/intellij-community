/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

public abstract class HttpFileSystem extends DeprecatedVirtualFileSystem {
  public static HttpFileSystem getInstance() {
    return (HttpFileSystem)VirtualFileManager.getInstance().getFileSystem(URLUtil.HTTP_PROTOCOL);
  }

  public abstract boolean isFileDownloaded(@NotNull VirtualFile file);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileListener(@NotNull HttpVirtualFileListener listener);

  public abstract VirtualFile createChild(@NotNull VirtualFile parent, @NotNull String name, boolean isDirectory);
}
