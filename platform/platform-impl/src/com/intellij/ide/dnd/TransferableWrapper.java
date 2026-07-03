// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.tree.TreePathUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Objects;

/**
* @author Konstantin Bulenkov
*/
public interface TransferableWrapper extends FileFlavorProvider {
  default TreePath @Nullable [] getTreePaths() {
    return TreePathUtil.toTreePaths(getTreeNodes());
  }

  TreeNode @Nullable [] getTreeNodes();

  PsiElement @Nullable [] getPsiElements();

  default VirtualFile @Nullable [] getVirtualFiles() {
    PsiElement[] elements = getPsiElements();
    if (elements == null) return null;
    return Arrays.stream(elements)
      .filter(PsiFileSystemItem.class::isInstance)
      .map(e -> ((PsiFileSystemItem)e).getVirtualFile())
      .filter(Objects::nonNull)
      .toArray(VirtualFile[]::new);
  }
}
