/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.chartostring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

class CharToStringPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression expression)) {
      return false;
    }
    final PsiType type = expression.getType();
    if (!PsiTypes.charType().equals(type)) {
      return false;
    }
    final String charLiteral = element.getText();
    if (charLiteral.length() < 2) return false; // Incomplete char literal probably without closing amp

    final String charText =
      charLiteral.substring(1, charLiteral.length() - 1);
    if (StringUtil.unescapeStringCharacters(charText).length() != 1) {
      // not satisfied with character literals of more than one character
      return false;
    }
    return isInConcatenationContext(expression);
  }

  static boolean isInConcatenationContext(PsiExpression element) {
    if (ExpressionUtils.isStringConcatenationOperand(element)) return true;
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    if (parent instanceof PsiAssignmentExpression parentExpression) {
      final IElementType tokenType = parentExpression.getOperationTokenType();
      if (!JavaTokenType.PLUSEQ.equals(tokenType)) {
        return false;
      }
      final PsiType parentType = parentExpression.getType();
      if (parentType == null) {
        return false;
      }
      final String parentTypeText = parentType.getCanonicalText();
      return JAVA_LANG_STRING.equals(parentTypeText);
    }
    if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCall)) {
        return false;
      }
      final PsiReferenceExpression methodExpression =
        methodCall.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      final PsiType type;
      if (qualifierExpression == null) {
        // to use the intention inside the source of
        // String and StringBuffer
        type = methodExpression.getType();
      }
      else {
        type = qualifierExpression.getType();
      }
      if (type == null) {
        return false;
      }
      final String className = type.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
          CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"append".equals(methodName) &&
            !"insert".equals(methodName)) {
          return false;
        }
        final PsiElement method = methodExpression.resolve();
        return method != null;
      }
      else if (JAVA_LANG_STRING.equals(className)) {
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"indexOf".equals(methodName) &&
            !"lastIndexOf".equals(methodName) &&
            !"replace".equals(methodName)) {
          return false;
        }
        final PsiElement method = methodExpression.resolve();
        return method != null;
      }
    }
    return false;
  }
}
