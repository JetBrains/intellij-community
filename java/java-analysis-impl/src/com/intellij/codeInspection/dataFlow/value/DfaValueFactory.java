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
 * Date: Feb 7, 2002
 * Time: 2:33:28 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DfaValueFactory {
  private final List<DfaValue> myValues = ContainerUtil.newArrayList();
  private final Map<Pair<DfaPsiType, DfaPsiType>, Boolean> myAssignableCache = ContainerUtil.newHashMap();
  private final Map<Pair<DfaPsiType, DfaPsiType>, Boolean> myConvertibleCache = ContainerUtil.newHashMap();
  private final Map<PsiType, DfaPsiType> myDfaTypes = ContainerUtil.newHashMap();
  private final boolean myHonorFieldInitializers;
  private final boolean myUnknownMembersAreNullable;

  public DfaValueFactory(boolean honorFieldInitializers, boolean unknownMembersAreNullable) {
    myHonorFieldInitializers = honorFieldInitializers;
    myUnknownMembersAreNullable = unknownMembersAreNullable;
    myValues.add(null);
    myVarFactory = new DfaVariableValue.Factory(this);
    myConstFactory = new DfaConstValue.Factory(this);
    myBoxedFactory = new DfaBoxedValue.Factory(this);
    myTypeFactory = new DfaTypeValue.Factory(this);
    myRelationFactory = new DfaRelationValue.Factory(this);
    myExpressionFactory = new DfaExpressionFactory(this);
  }

  public boolean isHonorFieldInitializers() {
    return myHonorFieldInitializers;
  }

  public boolean isUnknownMembersAreNullable() {
    return myUnknownMembersAreNullable;
  }

  @NotNull
  public DfaValue createTypeValue(@Nullable PsiType type, @NotNull Nullness nullability) {
    type = TypeConversionUtil.erasure(type);
    if (type == null) return DfaUnknownValue.getInstance();
    return getTypeFactory().createTypeValue(internType(type), nullability);
  }

  private DfaPsiType internType(@NotNull PsiType psiType) {
    DfaPsiType dfaType = myDfaTypes.get(psiType);
    if (dfaType == null) {
      myDfaTypes.put(psiType, dfaType = new DfaPsiType(psiType, myAssignableCache, myConvertibleCache));
    }
    return dfaType;
  }

  int registerValue(DfaValue value) {
    myValues.add(value);
    return myValues.size() - 1;
  }

  public DfaValue getValue(int id) {
    return myValues.get(id);
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

  private final DfaVariableValue.Factory myVarFactory;
  private final DfaConstValue.Factory myConstFactory;
  private final DfaBoxedValue.Factory myBoxedFactory;
  private final DfaTypeValue.Factory myTypeFactory;
  private final DfaRelationValue.Factory myRelationFactory;
  private final DfaExpressionFactory myExpressionFactory;

  @NotNull
  public DfaVariableValue.Factory getVarFactory() {
    return myVarFactory;
  }

  @NotNull
  public DfaConstValue.Factory getConstFactory() {
    return myConstFactory;
  }
  @NotNull
  public DfaBoxedValue.Factory getBoxedFactory() {
    return myBoxedFactory;
  }

  @NotNull
  public DfaTypeValue.Factory getTypeFactory() {
    return myTypeFactory;
  }

  @NotNull
  public DfaRelationValue.Factory getRelationFactory() {
    return myRelationFactory;
  }
}
