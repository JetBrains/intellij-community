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
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;

public class DfaValueFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.value.DfaValueFactory");

  private int myLastID;
  private TIntObjectHashMap<DfaValue> myValues;

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
          DfaConstValue constValue = getConstFactory().create((PsiVariable)psiSource);
          if (constValue != null) return constValue;
        }

        PsiVariable psiVariable = resolveVariable((PsiReferenceExpression)psiExpression);
        if (psiVariable != null) {
          result = getVarFactory().create(psiVariable, false);
        }
      }
    }
    else if (psiExpression instanceof PsiLiteralExpression) {
      result = getConstFactory().create((PsiLiteralExpression)psiExpression);
    }
    else if (psiExpression instanceof PsiNewExpression) {
      result = getNotNullFactory().create(psiExpression.getType());
    }
    else {
      final Object value = ConstantExpressionEvaluator.computeConstantExpression(psiExpression, new THashSet<PsiVariable>(), false);
      if (value != null) {
        result = getConstFactory().createFromValue(value, psiExpression.getType());
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

  public static void freeInstance() {
  }

  private final DfaVariableValue.Factory myVarFactory;
  private final DfaConstValue.Factory myConstFactory;
  private final DfaBoxedValue.Factory myBoxedFactory;
  private final DfaNotNullValue.Factory myNotNullFactory;
  private final DfaTypeValue.Factory myTypeFactory;
  private final DfaRelationValue.Factory myRelationFactory;


  public DfaVariableValue.Factory getVarFactory() {
    return myVarFactory;
  }

  public DfaConstValue.Factory getConstFactory() {
    return myConstFactory;
  }
  public DfaBoxedValue.Factory getBoxedFactory() {
    return myBoxedFactory;
  }

  public DfaNotNullValue.Factory getNotNullFactory() {
    return myNotNullFactory;
  }

  public DfaTypeValue.Factory getTypeFactory() {
    return myTypeFactory;
  }

  public DfaRelationValue.Factory getRelationFactory() {
    return myRelationFactory;
  }
}
