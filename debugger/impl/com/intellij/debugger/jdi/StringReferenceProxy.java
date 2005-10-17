/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.sun.jdi.StringReference;

public class StringReferenceProxy extends ObjectReferenceProxyImpl{
  private String myStringValue;

  public StringReferenceProxy(VirtualMachineProxyImpl virtualMachineProxy, StringReference objectReference) {
    super(virtualMachineProxy, objectReference);
  }

  public StringReference getStringReference() {
    return (StringReference)getObjectReference();
  }

  public String value() {
    checkValid();
    if (myStringValue == null) {
      myStringValue = getStringReference().value();
    }
    return myStringValue;
  }

  public void clearCaches() {
    myStringValue = null;
    super.clearCaches();
  }
}
