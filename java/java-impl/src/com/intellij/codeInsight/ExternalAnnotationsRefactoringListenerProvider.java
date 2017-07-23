/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalAnnotationsRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof PsiModifierListOwner) {
      Project project = element.getProject();
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);
      PsiAnnotation[] annotations = externalAnnotationsManager.findExternalAnnotations(modifierListOwner);
      if (annotations != null) {
        String oldExternalName = PsiFormatUtil.getExternalName(modifierListOwner, false, Integer.MAX_VALUE);
        if (oldExternalName == null) {
          return null;
        }
        return new RefactoringElementListener() {
          private void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            if (newElement instanceof PsiModifierListOwner) {
              externalAnnotationsManager.elementRenamedOrMoved((PsiModifierListOwner)newElement, oldExternalName);
            }
          }

          @Override
          public void elementMoved(@NotNull PsiElement newElement) {
            elementRenamedOrMoved(newElement);
          }

          @Override
          public void elementRenamed(@NotNull PsiElement newElement) {
            elementRenamedOrMoved(newElement);
          }
        };
      }
    }
    return null;
  }
}
