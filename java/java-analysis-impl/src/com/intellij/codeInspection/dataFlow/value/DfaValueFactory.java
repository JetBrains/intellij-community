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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 2:33:28 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DfaValueFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.value.DfaValueFactory");

  private int myLastID;
  private final TIntObjectHashMap<DfaValue> myValues;

  public DfaValueFactory() {
    myValues = new TIntObjectHashMap<DfaValue>();
    myLastID = 0;

    myVarFactory = new DfaVariableValue.Factory(this);
    myConstFactory = new DfaConstValue.Factory(this);
    myBoxedFactory = new DfaBoxedValue.Factory(this);
    myNotNullFactory = new DfaNotNullValue.Factory(this);
    myTypeFactory = new DfaTypeValue.Factory(this);
    myRelationFactory = new DfaRelationValue.Factory(this);
  }

  public DfaValue createTypeValueWithNullability(@Nullable PsiType type, Nullness nullability) {
    return nullability == Nullness.NOT_NULL ? getNotNullFactory().create(type) : getTypeFactory().create(type, nullability == Nullness.NULLABLE);
  }

   int createID() {
    myLastID++;
    LOG.assertTrue(myLastID >= 0, "Overflow");
    return myLastID;
  }

  void registerValue(DfaValue value) {
    myValues.put(value.getID(), value);
  }

  public DfaValue getValue(int id) {
    return myValues.get(id);
  }

  @Nullable
  public DfaValue createValue(PsiExpression psiExpression) {
    if (psiExpression instanceof PsiReferenceExpression) {
      return createReferenceValue((PsiReferenceExpression)psiExpression);
    }

    if (psiExpression instanceof PsiLiteralExpression) {
      return createLiteralValue((PsiLiteralExpression)psiExpression);
    }

    if (psiExpression instanceof PsiNewExpression) {
      return getNotNullFactory().create(psiExpression.getType());
    }

    final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(psiExpression, false);
    PsiType type = psiExpression.getType();
    if (value != null && type != null) {
      if (value instanceof String) {
        return getNotNullFactory().create(type); // Non-null string literal.
      }
      return getConstFactory().createFromValue(value, type, null);
    }

    return null;
  }

  @Nullable
  public DfaValue createLiteralValue(PsiLiteralExpression literal) {
    if (literal.getValue() instanceof String) {
      return getNotNullFactory().create(literal.getType()); // Non-null string literal.
    }
    return getConstFactory().create(literal);
  }

  private static boolean isNotNullExpression(PsiExpression initializer, PsiType type) {
    if (initializer instanceof PsiNewExpression) {
      return true;
    }
    if (initializer instanceof PsiPolyadicExpression) {
      if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public DfaValue createReferenceValue(PsiReferenceExpression referenceExpression) {
    PsiElement psiSource = referenceExpression.resolve();
    if (!(psiSource instanceof PsiVariable)) {
      return null;
    }

    final PsiVariable variable = (PsiVariable)psiSource;
    if (variable.hasModifierProperty(PsiModifier.FINAL) && !variable.hasModifierProperty(PsiModifier.TRANSIENT)) {
      DfaValue constValue = getConstFactory().create(variable);
      if (constValue != null) return constValue;

      PsiExpression initializer = variable.getInitializer();
      PsiType type = initializer == null ? null : initializer.getType();
      if (initializer != null && type != null && isNotNullExpression(initializer, type)) {
        return getNotNullFactory().create(type);
      }
    }

    if (!variable.hasModifierProperty(PsiModifier.VOLATILE) && isEffectivelyUnqualified(referenceExpression)) {
      return getVarFactory().createVariableValue(variable, referenceExpression.getType(), false, null, null);
    }

    return null;
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
  private final DfaNotNullValue.Factory myNotNullFactory;
  private final DfaTypeValue.Factory myTypeFactory;
  private final DfaRelationValue.Factory myRelationFactory;

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
  public DfaNotNullValue.Factory getNotNullFactory() {
    return myNotNullFactory;
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
