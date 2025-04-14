/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated prefer {@link AddAnnotationModCommandAction}. If you just need to add an annotation to Java code,
 * you may use {@link AddAnnotationPsiFix#addPhysicalAnnotationIfAbsent(String, PsiNameValuePair[], PsiAnnotationOwner)}.
 */
@Deprecated
public class AddAnnotationFix extends AddAnnotationPsiFix implements IntentionAction {
  public AddAnnotationFix(@NotNull String fqn, @NotNull PsiModifierListOwner modifierListOwner, String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }

  public AddAnnotationFix(@NotNull String fqn,
                          @NotNull PsiModifierListOwner modifierListOwner,
                          PsiNameValuePair @NotNull [] values,
                          String @NotNull ... annotationsToRemove) {
    super(fqn, modifierListOwner, values, annotationsToRemove);
  }

  public AddAnnotationFix(@NotNull String fqn,
                          @NotNull PsiModifierListOwner modifierListOwner,
                          PsiNameValuePair @NotNull [] values,
                          ExternalAnnotationsManager.AnnotationPlace place,
                          String @NotNull ... annotationsToRemove) {
    super(fqn, modifierListOwner, values, place, annotationsToRemove);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) {
      PsiElement psiElement = getStartElement();
      if (psiElement == null || psiElement.getContainingFile() != file) return false;
    }
    return isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix();
  }
}
