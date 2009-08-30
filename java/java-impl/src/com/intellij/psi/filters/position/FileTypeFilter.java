/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.filters.position;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeFilter implements ElementFilter {
  private final FileType myFileType;


  public FileTypeFilter(final FileType fileType) {
    myFileType = fileType;
  }


  public boolean isAcceptable(Object element, PsiElement context) {
    if (!(element instanceof PsiElement)) return false;
    PsiElement psiElement = (PsiElement)element;
    final PsiFile containingFile = psiElement.getContainingFile();
    return containingFile.getFileType().equals(myFileType);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
