// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class ObjectReferenceProxyImpl extends JdiProxy {
  private final ObjectReference myObjectReference;

  //caches
  private ReferenceType myReferenceType;
  private Type myType;
  private ThreeState myIsCollected = ThreeState.UNSURE;

  public ObjectReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, @NotNull ObjectReference objectReference) {
    super(virtualMachineProxy);
    myObjectReference = objectReference;
  }

  public ObjectReference getObjectReference() {
    checkValid();
    return myObjectReference;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    return (VirtualMachineProxyImpl)myTimer;
  }

  public ReferenceType referenceType() {
    checkValid();
    if (myReferenceType == null) {
      myReferenceType = getObjectReference().referenceType();
    }
    return myReferenceType;
  }

  public Type type() {
    checkValid();
    if (myType == null) {
      myType = getObjectReference().type();
    }
    return myType;
  }

  @NonNls
  public String toString() {
    final ObjectReference objectReference = getObjectReference();
    final String objRefString = objectReference != null ? objectReference.toString() : "[referenced object collected]";
    return "ObjectReferenceProxyImpl: " + objRefString + " " + super.toString();
  }

  public Map<Field, Value> getValues(List<? extends Field> list) {
    return getObjectReference().getValues(list);
  }

  public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {
    getObjectReference().setValue(field, value);
  }

  public boolean isCollected() {
    checkValid();
    if (myIsCollected != ThreeState.YES) {
      try {
        myIsCollected = ThreeState.fromBoolean(VirtualMachineProxyImpl.isCollected(myObjectReference));
      }
      catch (VMDisconnectedException ignored) {
        myIsCollected = ThreeState.YES;
      }
    }
    return myIsCollected.toBoolean();
  }

  public long uniqueID() {
    return getObjectReference().uniqueID();
  }

  /**
   * @return a list of waiting ThreadReferenceProxies
   */
  public List<ThreadReferenceProxyImpl> waitingThreads() throws IncompatibleThreadStateException {
    return ContainerUtil.map(getObjectReference().waitingThreads(), getVirtualMachineProxy()::getThreadReferenceProxy);
  }

  public ThreadReferenceProxyImpl owningThread() throws IncompatibleThreadStateException {
    return getVirtualMachineProxy().getThreadReferenceProxy(getObjectReference().owningThread());
  }

  public int entryCount() throws IncompatibleThreadStateException {
    return getObjectReference().entryCount();
  }

  public boolean equals(Object o) {
    if (!(o instanceof ObjectReferenceProxyImpl)) {
      return false;
    }
    if (this == o) return true;

    return myObjectReference.equals(((ObjectReferenceProxyImpl)o).myObjectReference);
  }


  public int hashCode() {
    return myObjectReference.hashCode();
  }

  /**
   * The advice to the proxy to clear cached data.
   */
  @Override
  protected void clearCaches() {
    if (myIsCollected == ThreeState.NO) {
      // clearing cache makes sense only if the object has not been collected yet
      myIsCollected = ThreeState.UNSURE;
    }
  }
}
