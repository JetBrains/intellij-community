package com.intellij.codeInspection.dataFlow.value;

public class DfaValue {
  private final int myID;
  protected DfaValueFactory myFactory;

  public DfaValue(final DfaValueFactory factory) {
    myFactory = factory;
    if (factory != null) {
      myID = factory.createID();
      factory.registerValue(this);
    }
    else {
      myID = 0;
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
