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
 * Date: Jan 28, 2002
 * Time: 6:31:08 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DfaVariableValue extends DfaValue {

  public static class Factory {
    private final DfaVariableValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaVariableValue>> myStringToObject = new HashMap<String, ArrayList<DfaVariableValue>>();
    private final DfaValueFactory myFactory;
    private final MultiMap<DfaVariableValue, DfaVariableValue> myQualifiersToChainedVariables = new MultiMap<DfaVariableValue, DfaVariableValue>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaVariableValue(factory);
    }

    public DfaVariableValue createVariableValue(PsiVariable myVariable, boolean isNegated) {
      return createVariableValue(myVariable, myVariable.getType(), isNegated, null, null);
    }
    @NotNull
    public DfaVariableValue createVariableValue(PsiVariable myVariable,
                                                @Nullable PsiType varType, boolean isNegated, @Nullable DfaVariableValue qualifier, @Nullable PsiMethod accessMethod) {
      mySharedInstance.myVariable = myVariable;
      mySharedInstance.myIsNegated = isNegated;
      mySharedInstance.myQualifier = qualifier;
      mySharedInstance.myAccessMethod = accessMethod;

      String id = mySharedInstance.toString();
      ArrayList<DfaVariableValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaVariableValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (DfaVariableValue aVar : conditions) {
          if (aVar.hardEquals(mySharedInstance)) return aVar;
        }
      }

      DfaVariableValue result = new DfaVariableValue(myVariable, varType, isNegated, myFactory, qualifier, accessMethod);
      if (qualifier != null) {
        myQualifiersToChainedVariables.putValue(qualifier, result);
      }
      conditions.add(result);
      return result;
    }

    public List<DfaVariableValue> getAllQualifiedBy(DfaVariableValue value) {
      ArrayList<DfaVariableValue> result = new ArrayList<DfaVariableValue>();
      for (DfaVariableValue directQualified : myQualifiersToChainedVariables.get(value)) {
        result.add(directQualified);
        result.addAll(getAllQualifiedBy(directQualified));
      }
      return result;
    }

  }

  private PsiVariable myVariable;
  private PsiType myVarType;
  private PsiMethod myAccessMethod;
  @Nullable private DfaVariableValue myQualifier;
  private boolean myIsNegated;
  private Nullness myInherentNullability;

  private DfaVariableValue(PsiVariable variable, PsiType varType, boolean isNegated, DfaValueFactory factory, @Nullable DfaVariableValue qualifier, PsiMethod accessMethod) {
    super(factory);
    myVariable = variable;
    myIsNegated = isNegated;
    myQualifier = qualifier;
    myVarType = varType;
    myAccessMethod = accessMethod;
  }

  private DfaVariableValue(DfaValueFactory factory) {
    super(factory);
    myVariable = null;
    myIsNegated = false;
  }

  @Nullable
  public PsiVariable getPsiVariable() {
    return myVariable;
  }

  @Nullable
  public PsiType getVariableType() {
    return myVarType;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Override
  public DfaVariableValue createNegated() {
    return myFactory.getVarFactory().createVariableValue(myVariable, myVarType, !myIsNegated, myQualifier, myAccessMethod);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myVariable == null) return "$currentException";
    return (myIsNegated ? "!" : "") + myVariable.getName() + (myQualifier == null ? "" : "|" + myQualifier.toString());
  }

  private boolean hardEquals(DfaVariableValue aVar) {
    return aVar.myVariable == myVariable &&
           aVar.myIsNegated == myIsNegated &&
           aVar.myAccessMethod == myAccessMethod &&
           (myQualifier == null ? aVar.myQualifier == null : myQualifier.hardEquals(aVar.myQualifier));
  }

  @Nullable
  public DfaVariableValue getQualifier() {
    return myQualifier;
  }

  public boolean isViaMethods() {
    return myAccessMethod != null || myQualifier != null && myQualifier.isViaMethods();
  }

  public Nullness getInherentNullability() {
    if (myInherentNullability != null) {
      return myInherentNullability;
    }

    return myInherentNullability = calcInherentNullability();
  }

  private Nullness calcInherentNullability() {
    PsiMethod accessMethod = myAccessMethod;
    Nullness nullability = DfaPsiUtil.getElementNullability(getVariableType(), accessMethod);
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    PsiVariable var = getPsiVariable();
    nullability = DfaPsiUtil.getElementNullability(getVariableType(), var);
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    if (var != null) {
      if (DfaPsiUtil.isNullableInitialized(var, true)) {
        return Nullness.NULLABLE;
      }
      if (DfaPsiUtil.isNullableInitialized(var, false)) {
        return Nullness.NOT_NULL;
      }
    }

    return Nullness.UNKNOWN;
  }

  public boolean isLocalVariable() {
    return myVariable instanceof PsiLocalVariable || myVariable instanceof PsiParameter;
  }

  public boolean isFlushableByCalls() {
    if (isLocalVariable()) return false;
    if (!myVariable.hasModifierProperty(PsiModifier.FINAL)) return true;
    return myQualifier != null && myQualifier.isFlushableByCalls();
  }

}
