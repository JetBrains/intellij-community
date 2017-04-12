/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a method which is handled as a field in DFA.
 *
 * @author Tagir Valeev
 */
public enum SpecialField {
  STRING_LENGTH(CommonClassNames.JAVA_LANG_STRING, "length", true, LongRangeSet.indexRange()) {
    @Override
    public DfaValue createFromConstant(DfaValueFactory factory, @NotNull Object obj) {
      return obj instanceof String ? factory.getConstFactory().createFromValue(((String)obj).length(), PsiType.INT, null) : null;
    }
  },
  COLLECTION_SIZE(CommonClassNames.JAVA_UTIL_COLLECTION, "size", false, LongRangeSet.indexRange());

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

  public boolean isFinal() {
    return myFinal;
  }

  public LongRangeSet getRange() {
    return myRange;
  }

  public String getMethodName() {
    return myMethodName;
  }

  /**
   * Checks whether supplied method is represented by this special field
   *
   * @param method method to test
   * @return true if supplied method is represented by this special field
   */
  public boolean isMyMethod(PsiMethod method) {
    return MethodUtils.methodMatches(method, myClassName, null, myMethodName);
  }

  /**
   * Returns a canonical method representing this special field
   *
   * @param psiClass a class for which the canonical method should be resolved
   * @return a canonical method representing this special field (located either in supplied class or superclass) or null
   * if invalid class is supplied.
   */
  @Nullable
  public PsiMethod getCanonicalMethod(@Nullable PsiClass psiClass) {
    if (psiClass == null) return null;
    if (!myClassName.equals(psiClass.getQualifiedName())) {
      PsiClass myClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(myClassName, psiClass.getResolveScope());
      if (!InheritanceUtil.isInheritorOrSelf(psiClass, myClass, true)) return null;
      psiClass = myClass;
    }
    PsiMethod[] methods = psiClass.findMethodsByName(myMethodName, false);
    return methods.length == 1 ? methods[0] : null;
  }

  public DfaValue createValue(DfaValueFactory factory, DfaValue qualifier) {
    if (qualifier instanceof DfaVariableValue) {
      DfaVariableValue variableValue = (DfaVariableValue)qualifier;
      PsiMethod method = getCanonicalMethod(PsiUtil.resolveClassInClassTypeOnly(variableValue.getVariableType()));
      if (method != null) {
        return factory.getVarFactory().createVariableValue(method, PsiType.INT, false, variableValue);
      }
    }
    if(qualifier instanceof DfaConstValue) {
      Object obj = ((DfaConstValue)qualifier).getValue();
      if(obj != null) {
        DfaValue value = createFromConstant(factory, obj);
        if(value != null) {
          return value;
        }
      }
    }
    return factory.getRangeFactory().create(myRange);
  }

  public DfaValue createFromConstant(DfaValueFactory factory, @NotNull Object obj) {
    return null;
  }

  /**
   * @return a list of method contracts which equivalent to checking this special field for zero
   */
  public List<MethodContract> getEmptyContracts() {
    ContractValue thisValue = ContractValue.specialField(ContractValue.qualifier(), this);
    ContractValue zeroValue = ContractValue.constant(0, PsiType.INT);
    return Arrays.asList(MethodContract.singleConditionContract(thisValue, DfaRelationValue.RelationType.EQ, zeroValue,
                                                                MethodContract.ValueConstraint.TRUE_VALUE),
                         MethodContract.trivialContract(MethodContract.ValueConstraint.FALSE_VALUE));
  }

  @Override
  public String toString() {
    return StringUtil.getShortName(myClassName)+"."+myMethodName+"()";
  }
}
