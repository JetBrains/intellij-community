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

package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

public class ReplaceConstructorUsageInfo extends FixableUsageInfo{
  private final PsiType myNewType;
  private @Nls String myConflict;

  public ReplaceConstructorUsageInfo(PsiNewExpression element, PsiType newType, final PsiClass[] targetClasses) {
    super(element);
    myNewType = newType;
    final PsiMethod[] constructors = targetClasses[0].getConstructors();
    final PsiMethod constructor = element.resolveConstructor();
    if (constructor == null) {
      if (element.getArgumentList() != null) {
        if (constructors.length == 1 && !constructors[0].getParameterList().isEmpty() || constructors.length > 1) {
          myConflict = JavaRefactoringBundle.message("inline.super.no.ctor");
        }
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
        myConflict = JavaRefactoringBundle.message("inline.super.no.ctor");
      }

    }

    PsiType type = element.getType();
    if (type == null) {
      appendConflict(JavaRefactoringBundle.message("inline.super.unknown.type"));
      return;
    } else {
      type = type.getDeepComponentType();
    }

    if (!TypeConversionUtil.isAssignable(type, newType)) {
      final String conflict = JavaRefactoringBundle.message("inline.super.type.params.differ", element.getText(), newType.getPresentableText(), type.getPresentableText());
      appendConflict(conflict);
    }

    if (targetClasses.length > 1) {
      final String conflict =
        JavaRefactoringBundle.message("inline.super.ctor.can.be.replaced", element.getText(),
                                      StringUtil.join(targetClasses, psiClass -> psiClass.getQualifiedName(), ", "));
      appendConflict(conflict);
    }
  }

  private void appendConflict(@Nls final String conflict) {
    if (myConflict == null) {
      myConflict = conflict;
    } else {
      myConflict += "\n" + conflict;
    }
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression newExpression = (PsiNewExpression)getElement();
    if (newExpression != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(newExpression.getProject());

      final StringBuilder buf = new StringBuilder();
      buf.append("new ").append(myNewType.getCanonicalText());
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      final PsiType newExpressionType = newExpression.getType();
      assert newExpressionType != null;
      if (arrayInitializer != null) {
        buf.append("[]".repeat(newExpressionType.getArrayDimensions()));
        buf.append(arrayInitializer.getText());
      }
      else {
        final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
        if (arrayDimensions.length > 0) {
          buf.append("[");
          buf.append(StringUtil.join(arrayDimensions, psiExpression -> psiExpression.getText(), "]["));
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

  @Override
  public String getConflictMessage() {
    return myConflict;
  }
}