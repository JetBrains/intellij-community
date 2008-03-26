/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class DfaVariableValue extends DfaValue {
  public static class Factory {
    private final DfaVariableValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaVariableValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaVariableValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaVariableValue>>();
    }

    public DfaVariableValue create(PsiVariable myVariable, boolean isNegated) {
      mySharedInstance.myVariable = myVariable;
      mySharedInstance.myIsNegated = isNegated;

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

      DfaVariableValue result = new DfaVariableValue(myVariable, isNegated, myFactory);
      conditions.add(result);
      return result;
    }
  }

  private PsiVariable myVariable;
  private boolean myIsNegated;

  private DfaVariableValue(PsiVariable variable, boolean isNegated, DfaValueFactory factory) {
    super(factory);
    myVariable = variable;
    myIsNegated = isNegated;
  }

  private DfaVariableValue(DfaValueFactory factory) {
    super(factory);
    myVariable = null;
    myIsNegated = false;
  }

  public PsiVariable getPsiVariable() {
    return myVariable;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  public DfaValue createNegated() {
    return myFactory.getVarFactory().create(getPsiVariable(), !myIsNegated);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myVariable == null) return "$currentException";
    return (myIsNegated ? "!" : "") + myVariable.getName();
  }

  private boolean hardEquals(DfaVariableValue aVar) {
    return aVar.myVariable == myVariable && aVar.myIsNegated == myIsNegated;
  }
}
