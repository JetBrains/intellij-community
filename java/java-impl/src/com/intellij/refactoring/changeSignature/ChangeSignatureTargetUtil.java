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

/*
 * User: anna
 * Date: 24-Nov-2009
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class ChangeSignatureTargetUtil {
  private ChangeSignatureTargetUtil() {}

  @Nullable
  public static PsiMember findTargetMember(PsiFile file, Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (PsiTreeUtil.getParentOfType(element, PsiParameterList.class) != null) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    final PsiCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
    if (expression != null) {
      assert element != null;
      final PsiExpression qualifierExpression = expression instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression()
                                                                                              : expression instanceof PsiNewExpression ? ((PsiNewExpression)expression).getQualifier() : null;
      if (PsiTreeUtil.isAncestor(qualifierExpression, element, false)) {
        final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(qualifierExpression, PsiExpressionList.class);
        if (expressionList != null) {
          final PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiMethodCallExpression) {
            return ((PsiMethodCallExpression)parent).resolveMethod();
          }
        }
      } else {
        return expression.resolveMethod();
      }
    }

    final PsiTypeParameterList typeParameterList = PsiTreeUtil.getParentOfType(element, PsiTypeParameterList.class);
    if (typeParameterList != null) {
      return PsiTreeUtil.getParentOfType(typeParameterList, PsiMember.class);
    }

    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
    if (referenceParameterList != null) {
      final PsiJavaCodeReferenceElement referenceElement =
        PsiTreeUtil.getParentOfType(referenceParameterList, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass) {
          return (PsiMember)resolved;
        }
        else if (resolved instanceof PsiMethod) {
          return (PsiMember)resolved;
        }
      }
    }
    return null;
  }

}
