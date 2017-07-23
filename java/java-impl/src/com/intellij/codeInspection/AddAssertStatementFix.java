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
package com.intellij.codeInspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AddAssertStatementFix");
  private final SmartPsiElementPointer<PsiExpression> myExpressionToAssert;
  private final String myText;

  public AddAssertStatementFix(@NotNull PsiExpression expressionToAssert) {
    myExpressionToAssert = SmartPointerManager.getInstance(expressionToAssert.getProject()).createSmartPsiElementPointer(expressionToAssert);
    LOG.assertTrue(PsiType.BOOLEAN.equals(expressionToAssert.getType()));
    myText = expressionToAssert.getText();
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.assert.quickfix", myText);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression expressionToAssert = myExpressionToAssert.getElement();
    if (expressionToAssert == null) return;
    PsiElement element = descriptor.getPsiElement();
    PsiElement anchorElement = RefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorElement != null);
    final PsiElement tempParent = anchorElement.getParent();
    if (tempParent instanceof PsiForStatement && !PsiTreeUtil.isAncestor(((PsiForStatement)tempParent).getBody(), anchorElement, false)) {
      anchorElement = tempParent;
    }
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorElement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    try {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
      @NonNls String text = "assert c;";
      PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText(text, null);
      final PsiExpression assertCondition = assertStatement.getAssertCondition();
      assert assertCondition != null;

      assertCondition.replace(expressionToAssert);
      final PsiElement parent = anchorElement.getParent();
      if (parent instanceof PsiCodeBlock) {
        parent.addBefore(assertStatement, anchorElement);
      }
      else {
        RefactoringUtil.putStatementInLoopBody(assertStatement, parent, anchorElement);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.quickfix.assert.family");
  }
}
