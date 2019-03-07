// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassAndFileEqualityProvider implements SEResultsEqualityProvider {
  @NotNull
  @Override
  public Action compareItems(@NotNull SESearcher.ElementInfo newItemInfo, @NotNull SESearcher.ElementInfo alreadyFoundItemInfo) {
    PsiElement newElementPsi = PsiElementsEqualityProvider.toPsi(newItemInfo.getElement());
    PsiElement alreadyFoundPsi = PsiElementsEqualityProvider.toPsi(alreadyFoundItemInfo.getElement());

    if (newElementPsi == null || alreadyFoundPsi == null) {
      return Action.DO_NOTHING;
    }

    if (isClassAndFile(newItemInfo, alreadyFoundItemInfo) && isSameFile(newElementPsi, alreadyFoundPsi)) {
      return newItemInfo.priority > alreadyFoundItemInfo.priority ? Action.REPLACE : Action.SKIP;
    }

    return Action.DO_NOTHING;
  }

  private static boolean isClassAndFile(@NotNull SESearcher.ElementInfo newItemInfo, @NotNull SESearcher.ElementInfo alreadyFoundItemInfo) {
    SearchEverywhereContributor<?> c1 = newItemInfo.getContributor();
    SearchEverywhereContributor<?> c2 = alreadyFoundItemInfo.getContributor();
    return c1 instanceof ClassSearchEverywhereContributor && c2 instanceof FileSearchEverywhereContributor
           || c2 instanceof ClassSearchEverywhereContributor && c1 instanceof FileSearchEverywhereContributor;
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
