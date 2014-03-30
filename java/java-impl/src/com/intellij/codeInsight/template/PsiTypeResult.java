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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max, dsl
 */
public class PsiTypeResult implements RecalculatableResult {
  private final SmartTypePointer myTypePointer;
  private final JavaPsiFacade myFacade;

  public PsiTypeResult(@NotNull PsiType type, Project project) {
    final PsiType actualType = PsiUtil.convertAnonymousToBaseType(type);
    myTypePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(actualType);
    myFacade = JavaPsiFacade.getInstance(project);
  }

  public PsiType getType() {
    return myTypePointer.getType();
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    if (text.length() == 0) return false;
    final PsiType type = getType();
    if (text.equals(type.getCanonicalText())) return true;
    try {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)myFacade.getElementFactory().createExpressionFromText("(" + text + ")a", context);
      final PsiTypeElement castType = cast.getCastType();
      return castType != null && castType.getType().equals(type);
    }
    catch (IncorrectOperationException e) {
      // Indeed, not equal if cannot parse to a type.
      return false;
    }
  }

  public String toString() {
    return getType().getCanonicalText();
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd);
  }

  @Override
  public void handleRecalc(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd);
  }
}
