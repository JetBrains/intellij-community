// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;


public class RenamePsiDirectoryProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiDirectory;
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog(@NotNull Project project, @NotNull PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new RenameWithOptionalReferencesDialog(project, element, nameSuggestionContext, editor) {
      @Override
      protected boolean getSearchForReferences() {
        return RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
      }

      @Override
      protected void setSearchForReferences(boolean value) {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
      }
    };
  }

  @Override
  public String getQualifiedNameAfterRename(@NotNull final PsiElement element, @NotNull final String newName, final boolean nonJava) {
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (psiPackage != null) {
      return RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, nonJava);
    }
    return newName;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInCommentsAndStrings) {
    if (!RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY) {
      return Collections.emptyList();
    }
    return ReferencesSearch.search(element, searchScope).findAll();
  }

  @Nullable
  @Override
  public PsiElement getElementToSearchInStringsAndComments(@NotNull PsiElement element) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) element);
    if (aPackage != null) return aPackage;
    return null;
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_DIRECTORY;
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
    }
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
    }
  }
}
