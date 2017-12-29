/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.jdi.ThreadGroupReferenceProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ThreadGroupReference;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class ThreadGroupReferenceProxyImpl extends ObjectReferenceProxyImpl implements ThreadGroupReferenceProxy{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl");
  //caches
  private ThreadGroupReferenceProxyImpl myParentThreadGroupProxy;
  private boolean myIsParentGroupCached = false;
  private String myName;

  public ThreadGroupReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadGroupReference threadGroupReference) {
    super(virtualMachineProxy, threadGroupReference);
  }

  public ThreadGroupReference getThreadGroupReference() {
    return (ThreadGroupReference)getObjectReference();
  }

  public String name() {
    checkValid();
    if (myName == null) {
      myName = getThreadGroupReference().name();
    }
    return myName;
  }

  public ThreadGroupReferenceProxyImpl parent() {
    checkValid();
    if (!myIsParentGroupCached) {
      myParentThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(getThreadGroupReference().parent());
      myIsParentGroupCached = true;
    }
    return myParentThreadGroupProxy;
  }

  public @NonNls String toString() {
    return "ThreadGroupReferenceProxy: " + getThreadGroupReference().toString();
  }

  public void suspend() {
    getThreadGroupReference().suspend();
  }

  public void resume() {
    getThreadGroupReference().resume();
  }

  public List<ThreadReferenceProxyImpl> threads() {
    return StreamEx.of(getThreadGroupReference().threads()).map(getVirtualMachineProxy()::getThreadReferenceProxy).toList();
  }

  public List<ThreadGroupReferenceProxyImpl> threadGroups() {
    return StreamEx.of(getThreadGroupReference().threadGroups()).map(getVirtualMachineProxy()::getThreadGroupReferenceProxy).toList();
  }

  public void clearCaches() {
//    myIsParentGroupCached = false;
//    myName = null;
//    myParentThreadGroupProxy = null;
    super.clearCaches();
  }
}
