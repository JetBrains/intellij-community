// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnFalse;
import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnTrue;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;

/**
 * Represents a method which is handled as a field in DFA.
 *
 * @author Tagir Valeev
 */
public enum SpecialField implements DfaVariableSource {
  ARRAY_LENGTH(null, "length", true, LongRangeSet.indexRange()) {
    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiField && "length".equals(accessor.getName()) &&
             JavaPsiFacade.getElementFactory(accessor.getProject()).getArrayClass(PsiUtil.getLanguageLevel(accessor)) ==
             accessor.getContainingClass();
    }

    @Override
    DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
      if (initializer instanceof PsiArrayInitializerExpression) {
        return factory.getInt(((PsiArrayInitializerExpression)initializer).getInitializers().length);
      }
      if (initializer instanceof PsiNewExpression) {
        PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        if (arrayInitializer != null) {
          return factory.getInt(arrayInitializer.getInitializers().length);
        }
        PsiExpression[] dimensions = ((PsiNewExpression)initializer).getArrayDimensions();
        if (dimensions.length > 0) {
          Object length = ExpressionUtils.computeConstantExpression(dimensions[0]);
          if (length instanceof Integer) {
            return factory.getInt(((Integer)length).intValue());
          }
        }
      }
      return null;
    }
  },
  STRING_LENGTH(CommonClassNames.JAVA_LANG_STRING, "length", true, LongRangeSet.indexRange()) {
    @Override
    DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
      Object value = ExpressionUtils.computeConstantExpression(initializer);
      if(value instanceof String) {
        return factory.getInt(((String)value).length());
      }
      return null;
    }

    @Override
    public DfaValue fromConstant(DfaValueFactory factory, @NotNull Object obj) {
      return obj instanceof String ? factory.getInt(((String)obj).length()) : null;
    }
  },
  COLLECTION_SIZE(CommonClassNames.JAVA_UTIL_COLLECTION, "size", false, LongRangeSet.indexRange()),
  MAP_SIZE(CommonClassNames.JAVA_UTIL_MAP, "size", false, LongRangeSet.indexRange());

  private final String myClassName;
  private final String myMethodName;
  private final boolean myFinal;
  private final LongRangeSet myRange;

  SpecialField(String className, String methodName, boolean isFinal, LongRangeSet range) {
    myClassName = className;
    myMethodName = methodName;
    myFinal = isFinal;
    myRange = range;
  }

  @Override
  public boolean isStable() {
    return myFinal;
  }

  public LongRangeSet getRange() {
    return myRange;
  }

  public String getMethodName() {
    return myMethodName;
  }

  /**
   * Checks whether supplied accessor (field or method) can be used to read this special field
   *
   * @param accessor accessor to test to test
   * @return true if supplied accessor can be used to read this special field
   */
  boolean isMyAccessor(PsiMember accessor) {
    return accessor instanceof PsiMethod && MethodUtils.methodMatches((PsiMethod)accessor, myClassName, null, myMethodName);
  }

  /**
   * Finds a special field which corresponds to given accessor (method or field)
   * @param accessor accessor to find a special field for
   * @return found special field or null if accessor cannot be used to access a special field
   */
  @Contract("null -> null")
  @Nullable
  public static SpecialField findSpecialField(PsiElement accessor) {
    if (!(accessor instanceof PsiMember)) return null;
    return StreamEx.of(values()).findFirst(sf -> sf.isMyAccessor((PsiMember)accessor)).orElse(null);
  }

  /**
   * Returns a DfaValue which represents this special field
   *
   * @param factory a factory to create new values if necessary
   * @param qualifier a known qualifier value
   * @return a DfaValue which represents this special field
   */
  public DfaValue createValue(DfaValueFactory factory, DfaValue qualifier) {
    if (qualifier instanceof DfaVariableValue) {
      DfaVariableValue variableValue = (DfaVariableValue)qualifier;
      PsiModifierListOwner psiVariable = variableValue.getPsiVariable();
      if (psiVariable instanceof PsiField &&
          factory.canTrustFieldInitializer((PsiField)psiVariable) &&
          psiVariable.hasModifierProperty(PsiModifier.STATIC) &&
          psiVariable.hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = ((PsiField)psiVariable).getInitializer();
        if (initializer != null) {
          DfaValue value = fromInitializer(factory, initializer);
          if (value != null) {
            return value;
          }
        }
      }
      return factory.getVarFactory().createVariableValue(this, PsiType.INT, variableValue);
    }
    if(qualifier instanceof DfaConstValue) {
      Object obj = ((DfaConstValue)qualifier).getValue();
      if(obj != null) {
        DfaValue value = fromConstant(factory, obj);
        if(value != null) {
          return value;
        }
      }
    }
    return factory.getFactValue(DfaFactType.RANGE, myRange);
  }

  DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
    return null;
  }

  DfaValue fromConstant(DfaValueFactory factory, @NotNull Object obj) {
    return null;
  }

  /**
   * @return a list of method contracts which equivalent to checking this special field for zero
   */
  public List<MethodContract> getEmptyContracts() {
    ContractValue thisValue = ContractValue.qualifier().specialField(this);
    return Arrays
      .asList(MethodContract.singleConditionContract(thisValue, DfaRelationValue.RelationType.EQ, ContractValue.zero(), returnTrue()),
              MethodContract.trivialContract(returnFalse()));
  }

  public List<MethodContract> getEqualsContracts() {
    return Arrays.asList(new StandardMethodContract(new StandardMethodContract.ValueConstraint[]{NULL_VALUE}, returnFalse()),
                         MethodContract.singleConditionContract(
                           ContractValue.qualifier().specialField(this), DfaRelationValue.RelationType.NE,
                           ContractValue.argument(0).specialField(this), returnFalse()));
  }

  @Override
  public String toString() {
    return myMethodName;
  }
}
