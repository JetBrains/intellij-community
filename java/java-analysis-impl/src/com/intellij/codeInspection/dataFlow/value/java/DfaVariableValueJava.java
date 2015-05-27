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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:08 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value.java;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;

public class DfaVariableValueJava extends DfaVariableValue {

  private static final ElementPattern<? extends PsiModifierListOwner> MEMBER_OR_METHOD_PARAMETER =
    or(psiMember(), psiParameter().withSuperParent(2, psiMember()));

  public static class FactoryImpl extends DfaVariableValue.Factory {

    public FactoryImpl(DfaValueFactory factory) {
      super(factory);
    }

    @NotNull
    @Override
    protected DfaVariableValue createConcrete(@NotNull PsiModifierListOwner variable,
                                                  @Nullable PsiType varType,
                                                  @Nullable DfaVariableValue qualifier,
                                                  boolean isNegated) {
      return new DfaVariableValueJava(variable, varType, isNegated, myFactory, qualifier);
    }
  }

  private DfaVariableValueJava(@NotNull PsiModifierListOwner variable,
                               @Nullable PsiType varType,
                               boolean isNegated,
                               DfaValueFactory factory,
                               @Nullable DfaVariableValue qualifier) {
    super(factory, variable, varType, qualifier, isNegated);
  }

  @Override
  protected Nullness calcInherentNullability() {
    PsiModifierListOwner var = getPsiVariable();
    Nullness nullability = DfaPsiUtil.getElementNullability(getVariableType(), var);
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    Nullness defaultNullability =
      myFactory.isUnknownMembersAreNullable() && MEMBER_OR_METHOD_PARAMETER.accepts(var) ? Nullness.NULLABLE : Nullness.UNKNOWN;

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaPsiUtil.getElementNullability(itemType, var);
        }
      }
    }

    if (var instanceof PsiField && DfaPsiUtil.isFinalField((PsiVariable)var) && myFactory.isHonorFieldInitializers()) {
      List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers((PsiField)var);
      if (initializers.isEmpty()) {
        return defaultNullability;
      }

      boolean hasUnknowns = false;
      for (PsiExpression expression : initializers) {
        if (!(expression instanceof PsiReferenceExpression)) {
          hasUnknowns = true;
          continue;
        }
        PsiElement target = ((PsiReferenceExpression)expression).resolve();
        if (!(target instanceof PsiParameter)) {
          hasUnknowns = true;
          continue;
        }
        if (NullableNotNullManager.isNullable((PsiParameter)target)) {
          return Nullness.NULLABLE;
        }
        if (!NullableNotNullManager.isNotNull((PsiParameter)target)) {
          hasUnknowns = true;
        }
      }

      if (hasUnknowns) {
        if (DfaPsiUtil.isInitializedNotNull((PsiField)var)) {
          return Nullness.NOT_NULL;
        }
        return defaultNullability;
      }

      return Nullness.NOT_NULL;
    }

    return defaultNullability;
  }

  @Override
  public boolean isFlushableByCalls() {
    if (myVariable instanceof PsiLocalVariable || myVariable instanceof PsiParameter) return false;
    if (myVariable instanceof PsiVariable && myVariable.hasModifierProperty(PsiModifier.FINAL)) {
      return myQualifier != null && myQualifier.isFlushableByCalls();
    }
    return true;
  }
}
