/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImplementAbstractClassMethodsFix extends ImplementMethodsFix {
  public ImplementAbstractClassMethodsFix(PsiElement highlightElement) {
    super(highlightElement);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (startElement instanceof PsiNewExpression) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      String startElementText = startElement.getText();
      try {
        PsiNewExpression newExpression =
          (PsiNewExpression)elementFactory.createExpressionFromText(startElementText + "{}", startElement);
        if (newExpression.getAnonymousClass() == null) {
          try {
            newExpression = (PsiNewExpression)elementFactory.createExpressionFromText(startElementText + "){}", startElement);
          }
          catch (IncorrectOperationException e) {
            return false;
          }
          if (newExpression.getAnonymousClass() == null) return false;
        }
      }
      catch (IncorrectOperationException e) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull PsiFile file,
                     @Nullable final Editor editor,
                     @NotNull final PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (editor == null) return;
    PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)startElement).getClassReference();
    if (classReference == null) return;
    final PsiClass psiClass = (PsiClass)classReference.resolve();
    if (psiClass == null) return;
    final JavaOverrideImplementMemberChooser chooser = chooseMethodsToImplement(editor, startElement, psiClass, false);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    OverrideOrImplementOptions options = chooser.getOptions();

    if (selectedElements == null || selectedElements.isEmpty()) return;

    WriteCommandAction.writeCommandAction(project, file).run(() -> {
      implementNewMethods(project, editor, startElement, selectedElements, options);
    });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement startElement = getStartElement();
    if (!(startElement instanceof PsiNewExpression newExpression)) {
      return IntentionPreviewInfo.EMPTY;
    }
    if (startElement.getContainingFile() != file.getOriginalFile()) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiNewExpression copyNewExpression = PsiTreeUtil.findSameElementInCopy(newExpression, file);
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return IntentionPreviewInfo.EMPTY;
    final PsiClass psiClass = (PsiClass)classReference.resolve();
    if (psiClass == null) return IntentionPreviewInfo.EMPTY;
    final Collection<CandidateInfo> overrideImplement =
      OverrideImplementExploreUtil.getMapToOverrideImplement(psiClass, true, false).values();
    List<PsiMethodMember> members = ContainerUtil.map(ContainerUtil.filter(overrideImplement,
                                                                       t -> t.getElement() instanceof PsiMethod method &&
                                                                            !method.hasModifierProperty(PsiModifier.DEFAULT)),
                                                  t -> new PsiMethodMember(t));
    implementNewMethods(project, editor, copyNewExpression, members, new OverrideOrImplementOptions());
    return IntentionPreviewInfo.DIFF;
  }

  private static void implementNewMethods(@NotNull Project project,
                                          @NotNull Editor editor,
                                          @NotNull PsiElement startElement,
                                          List<PsiMethodMember> selectedElements,
                                          OverrideOrImplementOptions options) {
    PsiNewExpression newExpression =
      (PsiNewExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText(startElement.getText() + "{}", startElement);
    newExpression = (PsiNewExpression)startElement.replace(newExpression);
    final PsiClass psiAnonClass = newExpression.getAnonymousClass();
    if (psiAnonClass == null) return;
    Map<PsiClass, PsiSubstitutor> subst = new HashMap<>();
    for (PsiMethodMember selectedElement : selectedElements) {
      final PsiClass baseClass = selectedElement.getElement().getContainingClass();
      if (baseClass != null) {
        PsiSubstitutor substitutor = subst.get(baseClass);
        if (substitutor == null) {
          substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiAnonClass, PsiSubstitutor.EMPTY);
          subst.put(baseClass, substitutor);
        }
        selectedElement.setSubstitutor(substitutor);
      }
    }
    OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiAnonClass, selectedElements, options);
  }
}
