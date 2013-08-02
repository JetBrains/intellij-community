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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceInaccessibleFieldWithGetterSetterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myMethodName;
  private final boolean myIsSetter;

  protected ReplaceInaccessibleFieldWithGetterSetterFix(@Nullable PsiElement element, PsiMethod getter, boolean isSetter) {
    super(element);
    myMethodName = getter.getName();
    myIsSetter = isSetter;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiReferenceExpression place = (PsiReferenceExpression)startElement;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(place)) return;
    String qualifier = null;
    final PsiExpression qualifierExpression = place.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifier = qualifierExpression.getText();
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression callExpression;
    final String call = (qualifier != null ? qualifier + "." : "") + myMethodName;
    if (!myIsSetter) {
      callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "()", null);
      callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
      place.replace(callExpression);
    } else {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
      if (parent instanceof PsiAssignmentExpression) {
        final PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
        final String argList = rExpression != null ? rExpression.getText() : "";
        callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "(" +   argList + ")", null);
        callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
        parent.replace(callExpression);
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    return myIsSetter ? "Replace with setter" : "Replace with getter";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with getter/setter";
  }

  public static void registerQuickFix(PsiMember refElement,
                                      PsiJavaCodeReferenceElement place,
                                      PsiClass accessObjectClass,
                                      HighlightInfo error) {
    if (refElement instanceof PsiField && place instanceof PsiReferenceExpression) {
      final PsiField psiField = (PsiField)refElement;
      final PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        if (PsiUtil.isOnAssignmentLeftHand((PsiExpression)place)) {
          final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(psiField);
          final PsiMethod setter = containingClass.findMethodBySignature(setterPrototype, true);
          if (setter != null && PsiUtil.isAccessible(setter, place, accessObjectClass)) {
            QuickFixAction.registerQuickFixAction(error, new ReplaceInaccessibleFieldWithGetterSetterFix(place, setter, true));
          }
        }
        else if (PsiUtil.isAccessedForReading((PsiExpression)place)) {
          final PsiMethod getterPrototype = GenerateMembersUtil.generateGetterPrototype(psiField);
          final PsiMethod getter = containingClass.findMethodBySignature(getterPrototype, true);
          if (getter != null && PsiUtil.isAccessible(getter, place, accessObjectClass)) {
            QuickFixAction.registerQuickFixAction(error, new ReplaceInaccessibleFieldWithGetterSetterFix(place, getter, false));
          }
        }
      }
    }
  }
}
