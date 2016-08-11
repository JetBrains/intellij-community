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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                     @Nullable("is null when called from inspection") final Editor editor,
                     @NotNull final PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiFile containingFile = startElement.getContainingFile();
    if (editor == null || !FileModificationService.getInstance().prepareFileForWrite(containingFile)) return;
    PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)startElement).getClassReference();
    if (classReference == null) return;
    final PsiClass psiClass = (PsiClass)classReference.resolve();
    if (psiClass == null) return;
    final MemberChooser<PsiMethodMember> chooser = chooseMethodsToImplement(editor, startElement, psiClass, false);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;

    new WriteCommandAction(project, file) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        PsiNewExpression newExpression =
          (PsiNewExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText(startElement.getText() + "{}", startElement);
        newExpression = (PsiNewExpression)startElement.replace(newExpression);
        final PsiClass psiClass = newExpression.getAnonymousClass();
        if (psiClass == null) return;
        Map<PsiClass, PsiSubstitutor> subst = new HashMap<>();
        for (PsiMethodMember selectedElement : selectedElements) {
          final PsiClass baseClass = selectedElement.getElement().getContainingClass();
          if (baseClass != null) {
            PsiSubstitutor substitutor = subst.get(baseClass);
            if (substitutor == null) {
              substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, PsiSubstitutor.EMPTY);
              subst.put(baseClass, substitutor);
            }
            selectedElement.setSubstitutor(substitutor);
          }
        }
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.isCopyJavadoc(),
                                                                     chooser.isInsertOverrideAnnotation());
      }
    }.execute();
  }
}
