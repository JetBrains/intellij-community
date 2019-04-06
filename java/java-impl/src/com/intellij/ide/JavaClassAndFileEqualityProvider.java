// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClassAndFileEqualityProvider implements SEResultsEqualityProvider {
  @NotNull
  @Override
  public SEEqualElementsActionType compareItems(@NotNull SearchEverywhereFoundElementInfo newItemInfo, @NotNull SearchEverywhereFoundElementInfo alreadyFoundItemInfo) {
    PsiElement newElementPsi = PsiElementsEqualityProvider.toPsi(newItemInfo.getElement());
    PsiElement alreadyFoundPsi = PsiElementsEqualityProvider.toPsi(alreadyFoundItemInfo.getElement());

    if (newElementPsi == null || alreadyFoundPsi == null) {
      return SEEqualElementsActionType.DO_NOTHING;
    }

    if (isClassAndFile(newItemInfo, alreadyFoundItemInfo) && isSameFile(newElementPsi, alreadyFoundPsi)) {
      return newItemInfo.priority > alreadyFoundItemInfo.priority ? SEEqualElementsActionType.REPLACE : SEEqualElementsActionType.SKIP;
    }

    return SEEqualElementsActionType.DO_NOTHING;
  }

  private static boolean isClassAndFile(@NotNull SearchEverywhereFoundElementInfo newItemInfo, @NotNull SearchEverywhereFoundElementInfo alreadyFoundItemInfo) {
    Object newElement = newItemInfo.getElement();
    Object oldElement = alreadyFoundItemInfo.getElement();

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
