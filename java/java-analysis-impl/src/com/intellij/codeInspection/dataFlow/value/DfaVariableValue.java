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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DfaVariableValue extends DfaValue {

  public abstract static class Factory {

    private final MultiMap<Trinity<Boolean,String,DfaVariableValue>,DfaVariableValue> myExistingVars = new MultiMap<Trinity<Boolean, String, DfaVariableValue>, DfaVariableValue>();
    protected final DfaValueFactory myFactory;

    protected Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaVariableValue createVariableValue(PsiVariable myVariable, boolean isNegated) {
      PsiType varType = myVariable.getType();
      if (varType instanceof PsiEllipsisType) {
        varType = new PsiArrayType(((PsiEllipsisType)varType).getComponentType());
      }
      return createVariableValue(myVariable, varType, isNegated, null);
    }

    public DfaVariableValue createVariableValue(@NotNull PsiModifierListOwner myVariable,
                                                @Nullable PsiType varType,
                                                boolean isNegated,
                                                @Nullable DfaVariableValue qualifier) {
      Trinity<Boolean,String,DfaVariableValue> key = Trinity.create(isNegated, ((PsiNamedElement)myVariable).getName(), qualifier);
      for (DfaVariableValue aVar : myExistingVars.get(key)) {
        if (aVar.hardEquals(myVariable, varType, isNegated, qualifier)) return aVar;
      }

      DfaVariableValue result = createConcrete(myVariable, varType, qualifier, isNegated);
      myExistingVars.putValue(key, result);
      while (qualifier != null) {
        qualifier.myDependents.add(result);
        qualifier = qualifier.getQualifier();
      }
      return result;
    }

    public List<DfaVariableValue> getAllQualifiedBy(DfaVariableValue value) {
      return value.myDependents;
    }

    protected abstract DfaVariableValue createConcrete(@NotNull PsiModifierListOwner variable,
                                                       @Nullable PsiType varType,
                                                       @Nullable DfaVariableValue qualifier,
                                                       boolean isNegated);
  }

  protected final DfaValueFactory myFactory;
  protected final PsiModifierListOwner myVariable;
  protected final PsiType myVarType;
  protected final boolean myIsNegated;
  protected final DfaVariableValue myQualifier;
  protected final DfaTypeValue myTypeValue;

  private DfaVariableValue myNegatedValue;
  private Nullness myInherentNullability;
  private final List<DfaVariableValue> myDependents = new SmartList<DfaVariableValue>();

  protected DfaVariableValue(DfaValueFactory factory,
                             @NotNull PsiModifierListOwner variable,
                             @Nullable PsiType varType,
                             @Nullable DfaVariableValue qualifier,
                             boolean isNegated) {
    super(factory);
    myFactory = factory;
    myVariable = variable;
    myVarType = varType;
    myIsNegated = isNegated;
    myQualifier = qualifier;

    DfaValue typeValue = factory.createTypeValue(varType, Nullness.UNKNOWN);
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

  @Override
  public String toString() {
    return (myIsNegated ? "!" : "") + ((PsiNamedElement)myVariable).getName() + (myQualifier == null ? "" : "|" + myQualifier.toString());
  }

  private boolean hardEquals(PsiModifierListOwner psiVar, PsiType varType, boolean negated, DfaVariableValue qualifier) {
    return psiVar == myVariable &&
           Comparing.equal(TypeConversionUtil.erasure(varType), TypeConversionUtil.erasure(myVarType)) &&
           negated == myIsNegated &&
           (myQualifier == null ? qualifier == null : myQualifier.hardEquals(qualifier.getPsiVariable(), qualifier.getVariableType(),
                                                                             qualifier.isNegated(), qualifier.getQualifier()));
  }

  @Nullable
  public DfaVariableValue getQualifier() {
    return myQualifier;
  }

  public Nullness getInherentNullability() {
    if (myInherentNullability != null) {
      return myInherentNullability;
    }

    return myInherentNullability = calcInherentNullability();
  }

  protected abstract Nullness calcInherentNullability();

  public abstract boolean isFlushableByCalls();

  public boolean containsCalls() {
    return myVariable instanceof PsiMethod || myQualifier != null && myQualifier.containsCalls();
  }

}
