package com.intellij.util;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;

public class PsiNavigateUtil {
  public static void navigate(@Nullable final PsiElement psiElement) {
    if (psiElement != null && psiElement.isValid()) {
      final PsiElement navigationElement = psiElement.getNavigationElement();
      final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
      final VirtualFile virtualFile = navigationElement.getContainingFile().getVirtualFile();
      if (virtualFile != null && virtualFile.isValid()) {
        new OpenFileDescriptor(navigationElement.getProject(), virtualFile, offset).navigate(true);
      }
    }
  }
}