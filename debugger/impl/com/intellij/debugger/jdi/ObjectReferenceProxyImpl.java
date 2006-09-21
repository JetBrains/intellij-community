/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ObjectReferenceProxyImpl extends JdiProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.ObjectReferenceProxyImpl");
  private final ObjectReference myObjectReference;

  //caches
  private ReferenceType myReferenceType;
  private Type myType;
  private Boolean myIsCollected = null;

  public ObjectReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ObjectReference objectReference) {
    super(virtualMachineProxy);
    LOG.assertTrue(objectReference != null);
    myObjectReference = objectReference;
  }

  public ObjectReference getObjectReference() {
    checkValid();
    return isCollected() ? null : myObjectReference;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    return (VirtualMachineProxyImpl) myTimer;
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

  public @NonNls String toString() {
    final ObjectReference objectReference = getObjectReference();
    //noinspection HardCodedStringLiteral
    final String objRefString = objectReference != null? objectReference.toString() : "[referenced object collected]";
    return "ObjectReferenceProxyImpl: " + objRefString + " " + super.toString();
  }

  public Map getValues(List list) {
    return getObjectReference().getValues(list);
  }

  public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {
    getObjectReference().setValue(field, value);
  }

  public boolean isCollected() {
    checkValid();
    if (myIsCollected == null || Boolean.FALSE.equals(myIsCollected)) {
      try {
        myIsCollected = Boolean.valueOf(VirtualMachineProxyImpl.isCollected(myObjectReference));
      }
      catch (VMDisconnectedException e) {
        myIsCollected = Boolean.TRUE;
      }
    }
    return myIsCollected.booleanValue();
  }

  public long uniqueID() {
    return getObjectReference().uniqueID();
  }

  /**
   * @return a list of waiting ThreadReferenceProxies
   * @throws IncompatibleThreadStateException
   */
  public List<ThreadReferenceProxyImpl> waitingThreads() throws IncompatibleThreadStateException {
    List<ThreadReference> list = getObjectReference().waitingThreads();
    List<ThreadReferenceProxyImpl> proxiesList = new ArrayList(list.size());

    for (Iterator<ThreadReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadReference threadReference = iterator.next();
      proxiesList.add(getVirtualMachineProxy().getThreadReferenceProxy(threadReference));
    }
    return proxiesList;
  }

  public ThreadReferenceProxyImpl owningThread() throws IncompatibleThreadStateException {
    ThreadReference threadReference = getObjectReference().owningThread();
    return getVirtualMachineProxy().getThreadReferenceProxy(threadReference);
  }

  public int entryCount() throws IncompatibleThreadStateException {
    return getObjectReference().entryCount();
  }

  public boolean equals(Object o) {
    if (!(o instanceof ObjectReferenceProxyImpl)) {
      return false;
    }
    if(this == o) return true;

    ObjectReference ref = myObjectReference;
    return ref != null ? ref.equals(((ObjectReferenceProxyImpl)o).myObjectReference) : false;
  }


  public int hashCode() {
    return myObjectReference.hashCode();
  }

  /**
   * The advice to the proxy to clear cached data.
   */
  protected void clearCaches() {
    if (Boolean.FALSE.equals(myIsCollected)) {
      // clearing cache makes sence only if the object has not been collected yet
      myIsCollected = null;
    }
  }
}
