/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.value.java;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DfaValueFactoryJava extends DfaValueFactory {

  private final DfaConstValueFactoryJava myConstFactory;
  private final DfaExpressionFactory myExpressionFactory;
  private final DfaVariableValueJava.FactoryImpl myVarFactory;

  public DfaValueFactoryJava(boolean honorFieldInitializers, boolean unknownMembersAreNullable) {
    super(honorFieldInitializers, unknownMembersAreNullable);
    myVarFactory = new DfaVariableValueJava.FactoryImpl(this);
    myConstFactory = new DfaConstValueFactoryJava(this);
    myExpressionFactory = new DfaExpressionFactory(this);
  }

  @Nullable
  public DfaValue createValue(PsiExpression psiExpression) {
    return myExpressionFactory.getExpressionDfaValue(psiExpression);
  }

  @Nullable
  public DfaValue createLiteralValue(PsiLiteralExpression literal) {
    if (literal.getValue() instanceof String) {
      return createTypeValue(literal.getType(), Nullness.NOT_NULL); // Non-null string literal.
    }
    return getConstFactory().create(literal);
  }

  @Nullable
  public static PsiVariable resolveUnqualifiedVariable(PsiReferenceExpression refExpression) {
    if (isEffectivelyUnqualified(refExpression)) {
      PsiElement resolved = refExpression.resolve();
      if (resolved instanceof PsiVariable) {
        return (PsiVariable)resolved;
      }
    }

    return null;
  }

  public static boolean isEffectivelyUnqualified(PsiReferenceExpression refExpression) {
    PsiExpression qualifier = refExpression.getQualifierExpression();
    if (qualifier == null) {
      return true;
    }
    if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiQualifiedExpression)qualifier).getQualifier();
      if (thisQualifier == null) return true;
      final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(refExpression, PsiClass.class);
      if (innerMostClass == thisQualifier.resolve()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public DfaVariableValueJava.FactoryImpl getVarFactory() {
    return myVarFactory;
  }

  @NotNull
  @Override
  public DfaConstValueFactoryJava getConstFactory() {
    return myConstFactory;
  }
}
