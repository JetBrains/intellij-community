// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

/**
 * @author peter
 */
public class PsiFileSystemItemUtil {
  @Nullable
  public static String findRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) {
    VirtualFile srcFile = src != null ? src.getVirtualFile() : null;
    VirtualFile dstFile = dst != null ? dst.getVirtualFile() : null;
    return srcFile != null && dstFile != null ? VfsUtilCore.findRelativePath(srcFile, dstFile, VFS_SEPARATOR_CHAR) : null;
  }

  @Nullable
  public static String getRelativePathFromAncestor(PsiFileSystemItem file, PsiFileSystemItem ancestor) {
    VirtualFile vFile = file != null ? file.getVirtualFile() : null;
    VirtualFile ancestorVFile = ancestor != null ? ancestor.getVirtualFile() : null;
    return vFile != null && ancestorVFile != null ? VfsUtilCore.getRelativePath(vFile, ancestorVFile, VFS_SEPARATOR_CHAR) : null;
  }
}