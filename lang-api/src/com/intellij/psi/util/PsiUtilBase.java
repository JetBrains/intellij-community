package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class PsiUtilBase {
  public static int getRootIndex(PsiElement root) {
    ASTNode node = root.getNode();
    while(node != null && node.getTreeParent() != null) {
      node = node.getTreeParent();
    }
    if(node != null) root = node.getPsi();
    final PsiFile containingFile = root.getContainingFile();
    final PsiFile[] psiRoots = containingFile.getPsiRoots();
    for (int i = 0; i < psiRoots.length; i++) {
      if(root == psiRoots[i]) return i;
    }
    throw new RuntimeException("invalid element");
  }

  @Nullable
  public static VirtualFile getVirtualFile(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return null;
    }

    if (element instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)element).getVirtualFile();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    return containingFile.getVirtualFile();
  }

}
