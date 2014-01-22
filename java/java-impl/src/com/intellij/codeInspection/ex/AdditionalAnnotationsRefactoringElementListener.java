/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdditionalAnnotationsRefactoringElementListener implements RefactoringElementListenerProvider {
  @Nullable
  @Override
  public RefactoringElementListener getListener(final PsiElement psiElement) {
    if (!(psiElement instanceof PsiClass)) return null;
    final String oldName = ((PsiClass)psiElement).getQualifiedName();
    if (oldName == null) return null;
    final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(psiElement.getProject());
    return new UndoRefactoringElementAdapter() {
      @Override
      protected void refactored(@NotNull PsiElement element, @Nullable String oldQualifiedName) {
        if (element instanceof PsiClass) {
          final String newQualifiedName = ((PsiClass)element).getQualifiedName();
          if (newQualifiedName != null) {
            final int idx = entryPointsManager.ADDITIONAL_ANNOTATIONS.indexOf(oldQualifiedName != null ? newQualifiedName : oldName);
            if (idx > -1) {
              entryPointsManager.ADDITIONAL_ANNOTATIONS.remove(idx);
              entryPointsManager.ADDITIONAL_ANNOTATIONS.add(idx, oldQualifiedName != null ? oldQualifiedName : newQualifiedName);
            }
          }
        }
      }
    };
  }
}
