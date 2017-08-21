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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class DfaVariableValue extends DfaValue {

  public static class Factory {
    private final MultiMap<Trinity<Boolean,String,DfaVariableValue>,DfaVariableValue> myExistingVars = new MultiMap<>();
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaVariableValue createVariableValue(PsiVariable myVariable, boolean isNegated) {
      PsiType varType = myVariable.getType();
      if (varType instanceof PsiEllipsisType) {
        varType = new PsiArrayType(((PsiEllipsisType)varType).getComponentType());
      }
      return createVariableValue(myVariable, varType, isNegated, null);
    }
    @NotNull
    public DfaVariableValue createVariableValue(@NotNull PsiModifierListOwner myVariable,
                                                @Nullable PsiType varType,
                                                boolean isNegated,
                                                @Nullable DfaVariableValue qualifier) {
      Trinity<Boolean,String,DfaVariableValue> key = Trinity.create(isNegated, ((PsiNamedElement)myVariable).getName(), qualifier);
      for (DfaVariableValue aVar : myExistingVars.get(key)) {
        if (aVar.hardEquals(myVariable, varType, isNegated, qualifier)) return aVar;
      }

      DfaVariableValue result = new DfaVariableValue(myVariable, varType, isNegated, myFactory, qualifier);
      myExistingVars.putValue(key, result);
      while (qualifier != null) {
        qualifier.myDependents.add(result);
        qualifier = qualifier.getQualifier();
      }
      return result;
    }

    @NotNull
    public List<DfaVariableValue> getAllQualifiedBy(@NotNull DfaVariableValue value) {
      return value.myDependents;
    }
  }

  private final PsiModifierListOwner myVariable;
  private final PsiType myVarType;
  @Nullable private final DfaVariableValue myQualifier;
  private DfaVariableValue myNegatedValue;
  private final boolean myIsNegated;
  private DfaFactMap myInherentFacts;
  private final DfaTypeValue myTypeValue;
  private final List<DfaVariableValue> myDependents = new SmartList<>();

  private DfaVariableValue(@NotNull PsiModifierListOwner variable, @Nullable PsiType varType, boolean isNegated, DfaValueFactory factory, @Nullable DfaVariableValue qualifier) {
    super(factory);
    myVariable = variable;
    myIsNegated = isNegated;
    myQualifier = qualifier;
    myVarType = varType;
    DfaValue typeValue = myFactory.createTypeValue(varType, Nullness.UNKNOWN);
    myTypeValue = typeValue instanceof DfaTypeValue ? (DfaTypeValue)typeValue : null;
    if (varType != null && !varType.isValid()) {
      PsiUtil.ensureValidType(varType, "Variable: " + variable + " of class " + variable.getClass());
    }
  }

  @Nullable
  public DfaTypeValue getTypeValue() {
    return myTypeValue;
  }

  @NotNull
  public PsiModifierListOwner getPsiVariable() {
    return myVariable;
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
    return myNegatedValue = myFactory.getVarFactory().createVariableValue(myVariable, myVarType, !myIsNegated, myQualifier);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return (myIsNegated ? "!" : "") + ((PsiNamedElement)myVariable).getName() + (myQualifier == null ? "" : "|" + myQualifier.toString());
  }

  private boolean hardEquals(PsiModifierListOwner psiVar, PsiType varType, boolean negated, DfaVariableValue qualifier) {
    return psiVar == myVariable &&
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
  public Nullness getInherentNullability() {
    return NullnessUtil.fromBoolean(getInherentFacts().get(DfaFactType.CAN_BE_NULL));
  }

  public boolean isFlushableByCalls() {
    if (myVariable instanceof PsiLocalVariable || myVariable instanceof PsiParameter || CFGBuilder.isTempVariable(myVariable)) {
      return false;
    }
    boolean finalField = myVariable instanceof PsiVariable && myVariable.hasModifierProperty(PsiModifier.FINAL);
    boolean specialFinalField = myVariable instanceof PsiMethod &&
                           Arrays.stream(SpecialField.values()).anyMatch(sf -> sf.isFinal() && sf.isMyAccessor(myVariable));
    if (finalField || specialFinalField) {
      return myQualifier != null && myQualifier.isFlushableByCalls();
    }
    return true;
  }

  public boolean containsCalls() {
    return myVariable instanceof PsiMethod || myQualifier != null && myQualifier.containsCalls();
  }

}
