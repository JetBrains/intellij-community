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
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
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
    private final MultiMap<Pair<String, DfaVariableValue>, DfaVariableValue> myExistingVars = new MultiMap<>();
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
      return createVariableValue(new DfaExpressionFactory.PlainDescriptor(variable), varType, qualifier);
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
      return createVariableValue(new DfaExpressionFactory.ThisDescriptor(aClass), type);
    }

    @NotNull
    public DfaVariableValue createVariableValue(@NotNull VariableDescriptor descriptor, @Nullable PsiType varType) {
      return createVariableValue(descriptor, varType, null);
    }

    @NotNull
    DfaVariableValue createVariableValue(@NotNull VariableDescriptor descriptor,
                                         @Nullable PsiType varType,
                                         @Nullable DfaVariableValue qualifier) {
      Pair<String, DfaVariableValue> key = Pair.create(descriptor.toString(), qualifier);
      for (DfaVariableValue aVar : myExistingVars.get(key)) {
        if (aVar.hardEquals(descriptor, varType, qualifier)) return aVar;
      }

      DfaVariableValue result = new DfaVariableValue(descriptor, varType, myFactory, qualifier);
      myExistingVars.putValue(key, result);
      while (qualifier != null) {
        qualifier.myDependents.add(result);
        qualifier = qualifier.getQualifier();
      }
      return result;
    }
  }

  @NotNull private final VariableDescriptor myDescriptor;
  private final PsiType myVarType;
  @Nullable private final DfaVariableValue myQualifier;
  private DfaFactMap myInherentFacts;
  private final DfaPsiType myDfaType;
  private final List<DfaVariableValue> myDependents = new SmartList<>();

  private DfaVariableValue(@NotNull VariableDescriptor descriptor,
                           @Nullable PsiType varType,
                           DfaValueFactory factory,
                           @Nullable DfaVariableValue qualifier) {
    super(factory);
    myDescriptor = descriptor;
    myQualifier = qualifier;
    myVarType = varType;
    myDfaType = varType == null ? null : myFactory.createDfaType(varType);
    if (varType != null && !varType.isValid()) {
      PsiUtil.ensureValidType(varType, "Variable: " + descriptor + " of class " + descriptor.getClass());
    }
  }

  @Nullable
  public DfaPsiType getDfaType() {
    return myDfaType;
  }

  @Nullable
  public PsiModifierListOwner getPsiVariable() {
    return myDescriptor.getPsiElement();
  }

  @NotNull
  public VariableDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  @Nullable
  public PsiType getType() {
    return myVarType;
  }

  @Override
  public DfaValue createNegated() {
    return myFactory.createCondition(this, DfaRelationValue.RelationType.EQ, myFactory.getBoolean(false));
  }

  /**
   * @return list of all variables created within the same factory which are directly or indirectly qualified by this variable.
   */
  @NotNull
  public List<DfaVariableValue> getDependentVariables() {
    return myDependents;
  }

  public int getDepth() {
    int depth = 0;
    DfaVariableValue qualifier = getQualifier();
    while (qualifier != null) {
      depth++;
      qualifier = qualifier.getQualifier();
    }
    return depth;
  }

  @NotNull
  @Contract(pure = true)
  public DfaVariableValue withQualifier(DfaVariableValue newQualifier) {
    return newQualifier == myQualifier ? this : myFactory.getVarFactory().createVariableValue(myDescriptor, myVarType, newQualifier);
  }

  public String toString() {
    return (myQualifier == null ? "" : myQualifier + ".") + myDescriptor;
  }

  private boolean hardEquals(VariableDescriptor descriptor, PsiType varType, DfaVariableValue qualifier) {
    return descriptor.equals(myDescriptor) && qualifier == myQualifier &&
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
    return DfaNullability.toNullability(getInherentFacts().get(DfaFactType.NULLABILITY));
  }

  public boolean isFlushableByCalls() {
    return !myDescriptor.isStable() || (myQualifier != null && myQualifier.isFlushableByCalls());
  }

  public boolean containsCalls() {
    return myDescriptor.isCall() || myQualifier != null && myQualifier.containsCalls();
  }
}
