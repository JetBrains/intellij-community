/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class RenamePsiDirectoryProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiDirectory;
  }

  @Override
  public RenameDialog createRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new RenameWithOptionalReferencesDialog(project, element, nameSuggestionContext, editor) {
      protected boolean getSearchForReferences() {
        return RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
      }

      protected void setSearchForReferences(boolean value) {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
      }
    };
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiDirectory aDirectory = (PsiDirectory) element;
    // rename all non-package statement references
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) != null) continue;
      RenameUtil.rename(usage, newName);
    }

    //rename package statement
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) == null) continue;
      RenameUtil.rename(usage, newName);
    }

    aDirectory.setName(newName);
    if (listener != null) {
      listener.elementRenamed(aDirectory);
    }
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (psiPackage != null) {
      return RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, nonJava);
    }
    return newName;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    if (!RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY) {
      return Collections.emptyList();
    }
    return ReferencesSearch.search(element).findAll();
  }

  @Nullable
  @Override
  public PsiElement getElementToSearchInStringsAndComments(PsiElement element) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) element);
    if (aPackage != null) return aPackage;
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_DIRECTORY;
  }

  public boolean isToSearchInComments(PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  public void setToSearchInComments(PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
    }
  }

  public boolean isToSearchForTextOccurrences(PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  public void setToSearchForTextOccurrences(PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
    }
  }
}
