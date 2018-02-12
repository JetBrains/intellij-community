// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class RenamePsiFileProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiFileSystemItem;
  }

  private static boolean getSearchForReferences(PsiElement element) {
    return element instanceof PsiFile
      ? RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE
      : RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element) {
    if (!getSearchForReferences(element)) {
      return Collections.emptyList();
    }
    return super.findReferences(element);
  }

  @Override
  public boolean isToSearchForReferencesEnabled(PsiElement element) {
    return true;
  }

  @Override
  public boolean isToSearchForReferences(PsiElement element) {
    return getSearchForReferences(element);
  }

  @Override
  public void setToSearchForReferences(PsiElement element, boolean value) {
    if (element instanceof PsiFile) {
      RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE = value;
    }
    else {
      RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
    }
  }
}
