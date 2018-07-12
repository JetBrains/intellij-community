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

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaFactMap;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DfaVariableValue extends DfaValue {

  public static class Factory {
    private final MultiMap<Trinity<Boolean,String,DfaVariableValue>,DfaVariableValue> myExistingVars = new MultiMap<>();
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @NotNull
    public DfaVariableValue createVariableValue(PsiVariable variable) {
      PsiType varType = variable.getType();
      if (varType instanceof PsiEllipsisType) {
        varType = ((PsiEllipsisType)varType).toArrayType();
      }
      DfaVariableValue qualifier = null;
      if (variable instanceof PsiField && !(variable.hasModifierProperty(PsiModifier.STATIC))) {
        qualifier = createThisValue(((PsiField)variable).getContainingClass());
      }
      return createVariableValue(new DfaExpressionFactory.PlainSource(variable), varType, qualifier);
    }

    /**
     * Creates a variable representing "this" value with given class as a context
     * @param aClass a class to bind "this" value to
     * @return a DFA variable
     */
    @Contract("null -> null; !null -> !null")
    public DfaVariableValue createThisValue(@Nullable PsiClass aClass) {
      if (aClass == null) return null;
      PsiClassType type = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
      return createVariableValue(new DfaExpressionFactory.ThisSource(aClass), type);
    }

    @NotNull
    public DfaVariableValue createVariableValue(@NotNull DfaVariableSource source, @Nullable PsiType varType) {
      return createVariableValue(source, varType, false, null);
    }

    @NotNull
    public DfaVariableValue createVariableValue(@NotNull DfaVariableSource source,
                                                @Nullable PsiType varType,
                                                @Nullable DfaVariableValue qualifier) {
      return createVariableValue(source, varType, false, qualifier);
    }

    @NotNull
    DfaVariableValue createVariableValue(@NotNull DfaVariableSource source,
                                         @Nullable PsiType varType,
                                         boolean isNegated,
                                         @Nullable DfaVariableValue qualifier) {
      Trinity<Boolean, String, DfaVariableValue> key = Trinity.create(isNegated, source.toString(), qualifier);
      for (DfaVariableValue aVar : myExistingVars.get(key)) {
        if (aVar.hardEquals(source, varType, isNegated, qualifier)) return aVar;
      }

      DfaVariableValue result = new DfaVariableValue(source, varType, isNegated, myFactory, qualifier);
      myExistingVars.putValue(key, result);
      while (qualifier != null) {
        qualifier.myDependents.add(result);
        qualifier = qualifier.getQualifier();
      }
      return result;
    }
  }

  @NotNull private final DfaVariableSource mySource;
  private final PsiType myVarType;
  @Nullable private final DfaVariableValue myQualifier;
  private DfaVariableValue myNegatedValue;
  private final boolean myIsNegated;
  private DfaFactMap myInherentFacts;
  private final DfaPsiType myDfaType;
  private final List<DfaVariableValue> myDependents = new SmartList<>();

  private DfaVariableValue(@NotNull DfaVariableSource source,
                           @Nullable PsiType varType,
                           boolean isNegated,
                           DfaValueFactory factory,
                           @Nullable DfaVariableValue qualifier) {
    super(factory);
    mySource = source;
    myIsNegated = isNegated;
    myQualifier = qualifier;
    myVarType = varType;
    myDfaType = varType == null ? null : myFactory.createDfaType(varType);
    if (varType != null && !varType.isValid()) {
      PsiUtil.ensureValidType(varType, "Variable: " + source + " of class " + source.getClass());
    }
  }

  @Nullable
  public DfaPsiType getDfaType() {
    return myDfaType;
  }

  @Nullable
  public PsiModifierListOwner getPsiVariable() {
    return mySource.getPsiElement();
  }

  @NotNull
  public DfaVariableSource getSource() {
    return mySource;
  }

  @Nullable
  public PsiType getVariableType() {
    return myVarType;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Nullable
  public DfaVariableValue getNegatedValue() {
    return myNegatedValue;
  }

  @Override
  public DfaVariableValue createNegated() {
    if (myNegatedValue != null) {
      return myNegatedValue;
    }
    return myNegatedValue = myFactory.getVarFactory().createVariableValue(mySource, myVarType, !myIsNegated, myQualifier);
  }

  /**
   * @return list of all variables created within the same factory which are directly or indirectly qualified by this variable.
   */
  @NotNull
  public List<DfaVariableValue> getDependentVariables() {
    return myDependents;
  }

  @NotNull
  public DfaVariableValue withQualifier(DfaVariableValue newQualifier) {
    return myFactory.getVarFactory().createVariableValue(mySource, myVarType, myIsNegated, newQualifier);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return (myIsNegated ? "!" : "") + (myQualifier == null ? "" : myQualifier + ".") + mySource;
  }

  private boolean hardEquals(DfaVariableSource source, PsiType varType, boolean negated, DfaVariableValue qualifier) {
    return source.equals(mySource) &&
           negated == myIsNegated &&
           qualifier == myQualifier &&
           Comparing.equal(TypeConversionUtil.erasure(varType), TypeConversionUtil.erasure(myVarType));
  }

  @Nullable
  public DfaVariableValue getQualifier() {
    return myQualifier;
  }

  public DfaFactMap getInherentFacts() {
    if(myInherentFacts == null) {
      myInherentFacts = DfaFactMap.calcFromVariable(this);
    }

    return myInherentFacts;
  }

  @NotNull
  public Nullability getInherentNullability() {
    return NullabilityUtil.fromBoolean(getInherentFacts().get(DfaFactType.CAN_BE_NULL));
  }

  public boolean isFlushableByCalls() {
    return !mySource.isStable() || (myQualifier != null && myQualifier.isFlushableByCalls());
  }

  public boolean containsCalls() {
    return mySource.isCall() || myQualifier != null && myQualifier.containsCalls();
  }
}
