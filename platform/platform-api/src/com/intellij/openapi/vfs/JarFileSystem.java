// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JarFileSystem extends ArchiveFileSystem implements JarCopyingFileSystem, LocalFileProvider, VfpCapableArchiveFileSystem {
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
