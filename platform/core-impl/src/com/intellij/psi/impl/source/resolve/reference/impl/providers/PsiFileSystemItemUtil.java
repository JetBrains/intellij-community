// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

public final class PsiFileSystemItemUtil {
  public static @Nullable String findRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) {
    VirtualFile srcFile = src != null ? src.getVirtualFile() : null;
    VirtualFile dstFile = dst != null ? dst.getVirtualFile() : null;
    return srcFile != null && dstFile != null ? VfsUtilCore.findRelativePath(srcFile, dstFile, VFS_SEPARATOR_CHAR) : null;
  }

  public static @Nullable String getRelativePathFromAncestor(PsiFileSystemItem file, PsiFileSystemItem ancestor) {
    VirtualFile vFile = file != null ? file.getVirtualFile() : null;
    VirtualFile ancestorVFile = ancestor != null ? ancestor.getVirtualFile() : null;
    return vFile != null && ancestorVFile != null ? VfsUtilCore.getRelativePath(vFile, ancestorVFile, VFS_SEPARATOR_CHAR) : null;
  }
}