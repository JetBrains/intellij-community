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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsParsingUtil");

  private ClsParsingUtil() { }

  public static PsiExpression createExpressionFromText(final String exprText, final PsiManager manager, final ClsElementImpl parent) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();
    final PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)parserFacade).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final PsiExpression expr;
    try {
      expr = parserFacade.createExpressionFromText(exprText, dummyJavaFile);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }

    if (expr instanceof PsiLiteralExpression) {
      PsiLiteralExpression literal = (PsiLiteralExpression)expr;
      return new ClsLiteralExpressionImpl(parent, exprText, literal.getType(), literal.getValue());
    }
    else if (expr instanceof PsiPrefixExpression) {
      PsiLiteralExpression operand = (PsiLiteralExpression)((PsiPrefixExpression)expr).getOperand();
      if (operand != null) {
        ClsLiteralExpressionImpl literalExpression =
          new ClsLiteralExpressionImpl(null, operand.getText(), operand.getType(), operand.getValue());
        ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literalExpression);
        literalExpression.setParent(prefixExpression);
        return prefixExpression;
      }
    }
    else if (expr instanceof PsiReferenceExpression) {
      PsiReferenceExpression patternExpr = (PsiReferenceExpression)expr;
      return new ClsReferenceExpressionImpl(parent, patternExpr);
    }
    else {
      final PsiConstantEvaluationHelper constantEvaluationHelper =
        JavaPsiFacade.getInstance(manager.getProject()).getConstantEvaluationHelper();
      Object value = constantEvaluationHelper.computeConstantExpression(expr);
      if (value != null) {
        return new ClsLiteralExpressionImpl(parent, exprText, expr.getType(), value); //it seems ok to make literal expression with non-literal text
      }
    }

    LOG.error(expr);
    return null;
  }
}
