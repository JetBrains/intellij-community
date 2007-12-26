package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
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

  public static int compareElementsByPosition(final PsiElement element1, final PsiElement element2) {
    if (element1 != null && element2 != null) {
      final PsiFile psiFile1 = element1.getContainingFile();
      final PsiFile psiFile2 = element2.getContainingFile();
      if (Comparing.equal(psiFile1, psiFile2)){
        final TextRange textRange1 = element1.getTextRange();
        final TextRange textRange2 = element2.getTextRange();
        if (textRange1 != null && textRange2 != null) {
          return textRange1.getStartOffset() - textRange2.getStartOffset();
        }
      } else if (psiFile1 != null && psiFile2 != null){
        final String name1 = psiFile1.getName();
        final String name2 = psiFile2.getName();
        return name1.compareToIgnoreCase(name2);
      }
    }
    return 0;
  }
}
