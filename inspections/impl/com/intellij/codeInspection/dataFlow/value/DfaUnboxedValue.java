package com.intellij.codeInspection.dataFlow.value;

import org.jetbrains.annotations.NonNls;

public class DfaUnboxedValue extends DfaValue {
  private final DfaVariableValue myVariable;

  DfaUnboxedValue(DfaVariableValue valueToWrap, DfaValueFactory factory) {
    super(factory);
    myVariable = valueToWrap;
  }

  @NonNls
  public String toString() {
    return "Unboxed "+myVariable.toString();
  }

  public DfaVariableValue getVariable() {
    return myVariable;
  }


  public boolean isNegated() {
    return myVariable.isNegated();
  }

  public DfaValue createNegated() {
    DfaVariableValue negVar = myFactory.getVarFactory().create(myVariable.getPsiVariable(), !myVariable.isNegated());
    return myFactory.getBoxedFactory().createUnboxed(negVar);
  }
}
