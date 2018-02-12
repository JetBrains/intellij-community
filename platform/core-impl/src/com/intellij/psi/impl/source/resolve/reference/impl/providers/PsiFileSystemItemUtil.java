// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.IncorrectOperationException;
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

  //<editor-fold desc="Deprecated stuff.">
  @Nullable
  private static PsiFileSystemItem getCommonAncestor(PsiFileSystemItem file1, PsiFileSystemItem file2) {
    if (file1 == file2) return file1;

    int depth1 = getDepth(file1);
    int depth2 = getDepth(file2);

    PsiFileSystemItem parent1 = file1;
    PsiFileSystemItem parent2 = file2;
    while (depth1 > depth2 && parent1 != null) {
      parent1 = parent1.getParent();
      depth1--;
    }
    while (depth2 > depth1 && parent2 != null) {
      parent2 = parent2.getParent();
      depth2--;
    }
    while (parent1 != null && parent2 != null && !parent1.equals(parent2)) {
      parent1 = parent1.getParent();
      parent2 = parent2.getParent();
    }
    return parent1;
  }

  private static int getDepth(PsiFileSystemItem file) {
    int depth = 0;
    while (file != null) {
      depth++;
      file = file.getParent();
    }
    return depth;
  }

  /** @deprecated incorrect when {@code src} is a directory; use {@link #findRelativePath(PsiFileSystemItem, PsiFileSystemItem)} instead */
  public static String getNotNullRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) throws IncorrectOperationException {
    final String s = getRelativePath(src, dst);
    if (s == null) throw new IncorrectOperationException("No way from " + src.getVirtualFile() + " to " + dst.getVirtualFile());
    return s;
  }

  /** @deprecated incorrect when {@code src} is a directory; use {@link #findRelativePath(PsiFileSystemItem, PsiFileSystemItem)} instead */
  @Nullable
  public static String getRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) {
    final PsiFileSystemItem commonAncestor = getCommonAncestor(src, dst);

    if (commonAncestor != null) {
      StringBuilder buffer = new StringBuilder();
      if (!src.equals(commonAncestor)) {
        while (!commonAncestor.equals(src.getParent())) {
          buffer.append("..").append('/');
          src = src.getParent();
          assert src != null;
        }
      }
      buffer.append(getRelativePathFromAncestor(dst, commonAncestor));
      return buffer.toString();
    }

    return null;
  }
  //</editor-fold>
}