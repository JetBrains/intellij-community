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
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;

/**
 * @author ven
 */
public class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsParsingUtil");
  public static PsiExpression createExpressionFromText(String exprText, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, dummyJavaFile).getTreeElement();
    CompositeElement _expr = ExpressionParsing.parseExpressionText(manager, exprText, 0, exprText.length(), holderElement.getCharTable());
    if (_expr == null){
      LOG.error("Could not parse expression:'" + exprText + "'");
      return null;
    }
    holderElement.rawAddChildren(_expr);
    PsiExpression expr = (PsiExpression)_expr.getPsi();
    if (expr instanceof PsiLiteralExpression){
      PsiLiteralExpression literal = (PsiLiteralExpression)expr;
      return new ClsLiteralExpressionImpl(parent, exprText, literal.getType(), literal.getValue());
    }
    else if (expr instanceof PsiPrefixExpression){
      PsiLiteralExpression operand = (PsiLiteralExpression)((PsiPrefixExpression)expr).getOperand();
      if (operand != null) {
        ClsLiteralExpressionImpl literalExpression = new ClsLiteralExpressionImpl(null, operand.getText(), operand.getType(), operand.getValue());
        ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literalExpression);
        literalExpression.setParent(prefixExpression);
        return prefixExpression;
      }
    }
    else if (expr instanceof PsiReferenceExpression){
      PsiReferenceExpression patternExpr = (PsiReferenceExpression)expr;
      return new ClsReferenceExpressionImpl(parent, patternExpr);
    }
    else{
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
