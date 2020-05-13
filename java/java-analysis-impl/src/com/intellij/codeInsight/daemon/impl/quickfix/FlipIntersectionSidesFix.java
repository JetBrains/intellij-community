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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FlipIntersectionSidesFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(FlipIntersectionSidesFix.class);
  private final String myClassName;
  private final List<PsiTypeElement> myConjuncts;
  private final PsiTypeElement myConjunct;
  private final PsiTypeElement myCastTypeElement;

  public FlipIntersectionSidesFix(String className,
                                  @NotNull List<PsiTypeElement> conjList,
                                  PsiTypeElement conjunct,
                                  PsiTypeElement castTypeElement) {
    myClassName = className;
    myConjuncts = conjList;
    LOG.assertTrue(!conjList.isEmpty());
    myConjunct = conjunct;
    myCastTypeElement = castTypeElement;
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
    for (PsiTypeElement typeElement : myConjuncts) {
      if (!typeElement.isValid()) return false;
    }
    return !Comparing.strEqual(myConjunct.getText(), myConjuncts.get(0).getText());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myConjuncts.remove(myConjunct);
    myConjuncts.add(0, myConjunct);

    final String intersectionTypeText = StringUtil.join(myConjuncts, element -> element.getText(), " & ");
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
