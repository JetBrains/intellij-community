package com.intellij.codeInspection.dataFlow.value;

public class DfaValue {
  private final int myID;
  protected final DfaValueFactory myFactory;

  protected DfaValue(final DfaValueFactory factory) {
    myFactory = factory;
    if (factory == null) {
      myID = 0;
    }
    else {
      myID = factory.createID();
      factory.registerValue(this);
    }
  }

  public int getID() {
    return myID;
  }

  public DfaValue createNegated() {
    return DfaUnknownValue.getInstance();
  }

  public boolean equals(Object obj) {
    return obj instanceof DfaValue && getID() == ((DfaValue)obj).getID();
  }

  public int hashCode() {
    return getID();
  }
}
