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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlipIntersectionSidesFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(FlipIntersectionSidesFix.class);
  private final String myClassName;
  private final PsiTypeElement myConjunct;
  private final PsiTypeElement myCastTypeElement;

  public FlipIntersectionSidesFix(String className,
                                  PsiTypeElement conjunct,
                                  PsiTypeElement castTypeElement) {
    myClassName = className;
    myConjunct = conjunct;
    myCastTypeElement = castTypeElement;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiTypeElement cast = PsiTreeUtil.findSameElementInCopy(myCastTypeElement, target);
    PsiTypeElement conjunct = PsiTreeUtil.findSameElementInCopy(myConjunct, target);
    return new FlipIntersectionSidesFix(myClassName, conjunct, cast);
  }

  @NotNull
  @Override
  public String getText() {
    return JavaAnalysisBundle.message("move.0.to.the.beginning", myClassName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("move.to.front");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myCastTypeElement.isValid() || !myConjunct.isValid()) return false;
    PsiTypeElement firstChild = PsiTreeUtil.findChildOfType(myCastTypeElement, PsiTypeElement.class);
    if (firstChild == null || myConjunct.textMatches(firstChild.getText())) return false;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(myCastTypeElement, PsiTypeElement.class);
    if (conjuncts == null) return;
    final String intersectionTypeText =
      StreamEx.of(conjuncts).without(myConjunct).prepend(myConjunct).map(PsiElement::getText).joining(" & ");
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiTypeCastExpression fixedCast =
      (PsiTypeCastExpression)elementFactory.createExpressionFromText("(" + intersectionTypeText + ") a", myCastTypeElement);
    final PsiTypeElement fixedCastCastType = fixedCast.getCastType();
    LOG.assertTrue(fixedCastCastType != null);
    final PsiElement flippedTypeElement = myCastTypeElement.replace(fixedCastCastType);
    CodeStyleManager.getInstance(project).reformat(flippedTypeElement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
