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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

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

  public DfaValue create(PsiExpression psiExpression) {
    DfaValue result = null;

    if (psiExpression instanceof PsiReferenceExpression) {
      PsiElement psiSource = ((PsiReferenceExpression)psiExpression).resolve();

      if (psiSource != null) {
        if (psiSource instanceof PsiVariable) {
          final PsiVariable variable = (PsiVariable)psiSource;
          DfaValue constValue = getConstFactory().create(variable);
          if (constValue != null) return constValue;

          if (!(psiSource instanceof PsiField)) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer instanceof PsiPolyadicExpression && variable.hasModifierProperty(PsiModifier.FINAL)) {
              PsiType type = initializer.getType();
              if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return getNotNullFactory().create(type);
              }
            }
          }
        }

        PsiVariable psiVariable = resolveVariable((PsiReferenceExpression)psiExpression);
        if (psiVariable != null) {
          result = getVarFactory().create(psiVariable, false);
        }
      }
    }
    else if (psiExpression instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literal = (PsiLiteralExpression)psiExpression;
      if (literal.getValue() instanceof String) {
        result = getNotNullFactory().create(psiExpression.getType()); // Non-null string literal.
      }
      else {
        result = getConstFactory().create(literal);
      }
    }
    else if (psiExpression instanceof PsiNewExpression) {
      result = getNotNullFactory().create(psiExpression.getType());
    }
    else {
      final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(psiExpression, false);
      PsiType type = psiExpression.getType();
      if (value != null && type != null) {
        if (value instanceof String) {
          result  = getNotNullFactory().create(type); // Non-null string literal.
        }
        else {
          result = getConstFactory().createFromValue(value, type);
        }
      }
    }

    return result;
  }

  public static PsiVariable resolveVariable(PsiReferenceExpression refExpression) {
    PsiExpression qualifier = refExpression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      PsiElement resolved = refExpression.resolve();
      if (resolved instanceof PsiVariable) {
        return (PsiVariable)resolved;
      }
    }

    return null;
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
