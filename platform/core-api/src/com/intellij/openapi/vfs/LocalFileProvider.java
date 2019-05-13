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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LocalFileProvider {
  /** @deprecated use {@link com.intellij.openapi.vfs.newvfs.ArchiveFileSystem#getLocalByEntry(com.intellij.openapi.vfs.VirtualFile)} instead (to be removed in IDEA 2019) */
  @Deprecated
  VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile);

  /** @deprecated use {@code ArchiveFileSystem.findFileByPath(path)} instead (to be removed in IDEA 2019) */
  @Deprecated
  VirtualFile findLocalVirtualFileByPath(@NotNull String path);
}