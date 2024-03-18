// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaClassAndFileEqualityProvider extends AbstractEqualityProvider {

  @Override
  protected boolean areEqual(@NotNull SearchEverywhereFoundElementInfo newItemInfo,
                             @NotNull SearchEverywhereFoundElementInfo alreadyFoundItemInfo) {
    PsiElement newElementPsi = PsiElementsEqualityProvider.toPsi(newItemInfo.getElement());
    PsiElement alreadyFoundPsi = PsiElementsEqualityProvider.toPsi(alreadyFoundItemInfo.getElement());

    return newElementPsi != null && alreadyFoundPsi != null
           && newElementPsi.getLanguage().isKindOf(JavaLanguage.INSTANCE)
           && alreadyFoundPsi.getLanguage().isKindOf(JavaLanguage.INSTANCE)
           && isClassAndFile(newElementPsi, alreadyFoundPsi)
           && isSameFile(newElementPsi, alreadyFoundPsi);
  }

  private static boolean isClassAndFile(@NotNull PsiElement newElement, @NotNull PsiElement oldElement) {
    return isClass(newElement) && isFile(oldElement)
           || isClass(oldElement) && isFile(newElement);
  }

  private static boolean isFile(Object element) {
    return element instanceof PsiFile || element instanceof VirtualFile;
  }

  private static boolean isClass(Object element) {
    return element instanceof PsiClass;
  }

  private static boolean isSameFile(@NotNull PsiElement newItem, @NotNull PsiElement alreadyFound) {
    VirtualFile newItemFile = convertToFileIsPossible(newItem);
    VirtualFile foundItemFile = convertToFileIsPossible(alreadyFound);
    return newItemFile != null && newItemFile.equals(foundItemFile);
  }

  @Nullable
  private static VirtualFile convertToFileIsPossible(@NotNull PsiElement element) {
    if (element instanceof VirtualFile) {
      return  (VirtualFile) element;
    } else if (element instanceof PsiFile) {
      return ((PsiFile) element).getVirtualFile();
    } else if (element instanceof PsiDirectory) {
      return  ((PsiDirectory) element).getVirtualFile();
    } else if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      VirtualFile virtualFile = SearchEverywhereClassifier.EP_Manager.getVirtualFile(element);
      if (virtualFile != null) {
        if (StringUtil.equals(name, virtualFile.getNameWithoutExtension())) {
          return virtualFile;
        }
      }
    }

    return null;
  }
}
