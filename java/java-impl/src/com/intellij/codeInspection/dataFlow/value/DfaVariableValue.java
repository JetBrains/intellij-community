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

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
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
      return createVariableValue(myVariable, myVariable.getType(), isNegated, null, false);
    }
    public DfaVariableValue createVariableValue(PsiVariable myVariable,
                                                @Nullable PsiType varType, boolean isNegated, @Nullable DfaVariableValue qualifier, boolean viaMethods) {
      mySharedInstance.myVariable = myVariable;
      mySharedInstance.myIsNegated = isNegated;
      mySharedInstance.myQualifier = qualifier;
      mySharedInstance.myViaMethods = viaMethods;

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

      DfaVariableValue result = new DfaVariableValue(myVariable, varType, isNegated, myFactory, qualifier, viaMethods);
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
  @Nullable private DfaVariableValue myQualifier;
  private boolean myIsNegated;
  private boolean myViaMethods;

  private DfaVariableValue(PsiVariable variable, PsiType varType, boolean isNegated, DfaValueFactory factory, @Nullable DfaVariableValue qualifier, boolean viaMethods) {
    super(factory);
    myVariable = variable;
    myIsNegated = isNegated;
    myQualifier = qualifier;
    myViaMethods = viaMethods;
    myVarType = varType;
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
    return myVariable == null ? null : myVariable.getType();
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  public DfaVariableValue createNegated() {
    return myFactory.getVarFactory().createVariableValue(myVariable, myVarType, !myIsNegated, myQualifier, myViaMethods);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myVariable == null) return "$currentException";
    return (myIsNegated ? "!" : "") + myVariable.getName() + (myQualifier == null ? "" : "|" + myQualifier.toString());
  }

  private boolean hardEquals(DfaVariableValue aVar) {
    return aVar.myVariable == myVariable &&
           aVar.myIsNegated == myIsNegated &&
           aVar.myViaMethods == myViaMethods &&
           (myQualifier == null ? aVar.myQualifier == null : myQualifier.hardEquals(aVar.myQualifier));
  }

  @Nullable
  public DfaVariableValue getQualifier() {
    return myQualifier;
  }

  public boolean isViaMethods() {
    return myViaMethods;
  }
}
