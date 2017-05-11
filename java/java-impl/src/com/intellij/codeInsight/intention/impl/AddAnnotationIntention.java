/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class AddAnnotationIntention extends BaseIntentionAction {
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  @NotNull
  public abstract Pair<String, String[]> getAnnotations(@NotNull Project project);

  // include not in project files
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    Pair<String, String[]> annotations = getAnnotations(project);
    String toAdd = annotations.first;
    String[] toRemove = annotations.second;
    if (toRemove.length > 0 && isAnnotatedSkipInferred(owner, toRemove)) {
      return false;
    }
    setText(AddAnnotationPsiFix.calcText(owner, toAdd));
    if (isAnnotatedSkipInferred(owner, toAdd)) return false;

    return canAnnotate(owner);
  }

  protected boolean canAnnotate(@NotNull PsiModifierListOwner owner) {
    return true;
  }

  private static boolean isAnnotatedSkipInferred(PsiModifierListOwner owner, String... annoFqns) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, false, annoFqns);
    return annotation != null && !AnnotationUtil.isInferredAnnotation(annotation);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || !owner.isValid()) return;
    Pair<String, String[]> annotations = getAnnotations(project);
    String toAdd = annotations.first;
    String[] toRemove = annotations.second;
    AddAnnotationFix fix = new AddAnnotationFix(toAdd, owner, toRemove);
    fix.invoke(project, editor, file);
  }
}