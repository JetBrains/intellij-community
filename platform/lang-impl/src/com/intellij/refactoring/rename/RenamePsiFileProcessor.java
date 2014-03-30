/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

  @Override
  public RenameDialog createRenameDialog(Project project, final PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new PsiFileRenameDialog(project, element, nameSuggestionContext, editor);
  }

  private static boolean getSearchForReferences(PsiElement element) {
    return element instanceof PsiFile
      ? RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE
      : RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    if (!getSearchForReferences(element)) {
      return Collections.emptyList();
    }
    return super.findReferences(element);
  }

  protected static class PsiFileRenameDialog extends RenameWithOptionalReferencesDialog {
    public PsiFileRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
      super(project, element, nameSuggestionContext, editor);
    }

    @Override
    protected boolean getSearchForReferences() {
      return RenamePsiFileProcessor.getSearchForReferences(getPsiElement());
    }

    @Override
    protected void setSearchForReferences(boolean value) {
      if (getPsiElement() instanceof PsiFile) {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_FILE = value;
      }
      else {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
      }
    }
  }
}
