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

import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JarFileSystem extends ArchiveFileSystem implements JarCopyingFileSystem, LocalFileProvider {
  public static final String PROTOCOL = StandardFileSystems.JAR_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.JAR_PROTOCOL_PREFIX;
  public static final String JAR_SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static JarFileSystem getInstance() {
    return (JarFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Nullable
  public VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryFile) {
    return entryFile == null ? null : getLocalByEntry(entryFile);
  }

  @Nullable
  public VirtualFile getJarRootForLocalFile(@NotNull VirtualFile file) {
    return getRootByLocal(file);
  }

  //<editor-fold desc="Deprecated stuff.">
  @Nullable
  @Override
  public VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile) {
    return getVirtualFileForJar(entryVFile);
  }

  @Nullable
  @Override
  public VirtualFile findLocalVirtualFileByPath(@NotNull String path) {
    if (!path.contains(JAR_SEPARATOR)) {
      path += JAR_SEPARATOR;
    }
    return findFileByPath(path);
  }
  //</editor-fold>
}
