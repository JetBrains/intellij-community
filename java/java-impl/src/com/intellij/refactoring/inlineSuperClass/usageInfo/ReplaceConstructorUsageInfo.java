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
 * Date: 29-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;

public class ReplaceConstructorUsageInfo extends FixableUsageInfo{
  private final PsiType myNewType;
  private String myConflict;
  private static final String CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND = "Constructor matching super not found";

  public ReplaceConstructorUsageInfo(PsiNewExpression element, PsiType newType, final PsiClass[] targetClasses) {
    super(element);
    myNewType = newType;
    final PsiMethod[] constructors = targetClasses[0].getConstructors();
    final PsiMethod constructor = element.resolveConstructor();
    if (constructor == null) {
      if (constructors.length == 1 && constructors[0].getParameterList().getParametersCount() > 0 || constructors.length > 1) {
        myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
      }
    } else {
      final PsiParameter[] superParameters = constructor.getParameterList().getParameters();
      boolean foundMatchingConstructor = constructors.length == 0 && superParameters.length == 0;
      constr: for (PsiMethod method : constructors) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (superParameters.length == parameters.length) {
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (!TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(parameter.getType()),
                                                 TypeConversionUtil.erasure(superParameters[i].getType()))) {
              continue constr;
            }
          }
          foundMatchingConstructor = true;
        }
      }
      if (!foundMatchingConstructor) {
        myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
      }

    }

    PsiType type = element.getType();
    if (type == null) {
      appendConflict("Type is unknown");
      return;
    } else {
      type = type.getDeepComponentType();
    }

    if (!TypeConversionUtil.isAssignable(type, newType)) {
      final String conflict = "Type parameters do not agree in " + element.getText() + ". " +
                              "Expected " + newType.getPresentableText() + " but found " + type.getPresentableText();
      appendConflict(conflict);
    }

    if (targetClasses.length > 1) {
      final String conflict = "Constructor " + element.getText() + " can be replaced with any of " + StringUtil.join(targetClasses, new Function<PsiClass, String>() {
        public String fun(final PsiClass psiClass) {
          return psiClass.getQualifiedName();
        }
      }, ", ");
      appendConflict(conflict);
    }
  }

  private void appendConflict(final String conflict) {
    if (myConflict == null) {
      myConflict = conflict;
    } else {
      myConflict += "\n" + conflict;
    }
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression newExpression = (PsiNewExpression)getElement();
    if (newExpression != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();

      final StringBuffer buf = new StringBuffer();
      buf.append("new ").append(myNewType.getCanonicalText());
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      final PsiType newExpressionType = newExpression.getType();
      assert newExpressionType != null;
      if (arrayInitializer != null) {
        for (int i = 0; i < newExpressionType.getArrayDimensions(); i++) {
          buf.append("[]");
        }
        buf.append(arrayInitializer.getText());
      }
      else {
        final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
        if (arrayDimensions.length > 0) {
          buf.append("[");
          buf.append(StringUtil.join(arrayDimensions, new Function<PsiExpression, String>() {
            public String fun(PsiExpression psiExpression) {
              return psiExpression.getText();
            }
          }, "]["));
          buf.append("]");
          for (int i = 0; i < newExpressionType.getArrayDimensions() - arrayDimensions.length; i++) {
            buf.append("[]");
          }
        } else {
          final PsiExpressionList list = newExpression.getArgumentList();
          if (list != null) {
            buf.append(list.getText());
          }
        }
      }

      newExpression.replace(elementFactory.createExpressionFromText(buf.toString(), newExpression));
    }
  }

  public String getConflictMessage() {
    return myConflict;
  }
}