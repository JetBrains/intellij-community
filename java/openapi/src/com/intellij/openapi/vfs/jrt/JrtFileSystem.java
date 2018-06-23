// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.jrt;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfpCapableArchiveFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

public abstract class JrtFileSystem extends ArchiveFileSystem implements VfpCapableArchiveFileSystem {
  public static final String PROTOCOL = StandardFileSystems.JRT_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.JRT_PROTOCOL_PREFIX;
  public static final String SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static boolean isRoot(@NotNull VirtualFile file) {
    return file.getParent() == null && file.getFileSystem() instanceof JrtFileSystem;
  }

  public static boolean isModuleRoot(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && isRoot(parent);
  }
}