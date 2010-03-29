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
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AddAssertStatementFix");
  private final PsiExpression myExpressionToAssert;

  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.assert.quickfix", myExpressionToAssert.getText());
  }

  public AddAssertStatementFix(PsiExpression expressionToAssert) {
    myExpressionToAssert = expressionToAssert;
    LOG.assertTrue(PsiType.BOOLEAN.equals(myExpressionToAssert.getType()));
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(descriptor.getPsiElement())) return;
    PsiElement element = descriptor.getPsiElement();
    PsiElement anchorElement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorElement != null);
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(anchorElement, PsiWhiteSpace.class);
    if (prev instanceof PsiComment && SuppressManager.getInstance().getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    try {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
      @NonNls String text = "assert c;";
      PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText(text, null);
      final PsiExpression assertCondition = assertStatement.getAssertCondition();
      assert assertCondition != null;
      assertCondition.replace(myExpressionToAssert);
      final PsiElement parent = anchorElement.getParent();
      if (parent instanceof PsiCodeBlock) {
        parent.addBefore(assertStatement, anchorElement);
      }
      else {
        PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", null);
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        block.add(assertStatement);
        block.add(anchorElement);
        anchorElement.replace(blockStatement);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.quickfix.assert.family");
  }
}
