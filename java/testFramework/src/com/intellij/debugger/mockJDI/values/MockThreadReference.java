/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.*;

import java.util.List;

public class MockThreadReference extends MockObjectReference implements ThreadReference {
  public MockThreadReference(final MockVirtualMachine virtualMachine) {
    this(virtualMachine, Thread.currentThread());
  }

  public MockThreadReference(final MockVirtualMachine virtualMachine, Thread thread) {
    super(virtualMachine, thread);
  }

  public Thread getThread() {
    return (Thread) getValue();
  }

  @Override
  public String name() {
    return getThread().getName();
  }

  @Override
  public void suspend() {
  }

  @Override
  public void resume() {
  }

  @Override
  public int suspendCount() {
    return 0;
  }

  @Override
  public void stop(ObjectReference objectReference) {
  }

  @Override
  public void interrupt() {
  }

  @Override
  public int status() {
    return ThreadReference.THREAD_STATUS_ZOMBIE;
  }

  @Override
  public boolean isSuspended() {
    return true;
  }

  @Override
  public boolean isAtBreakpoint() {
    return false;
  }

  @Override
  public ThreadGroupReference threadGroup() {
    throw new UnsupportedOperationException("Not implemented: \"threadGroup\" in " + getClass().getName());
  }

  @Override
  public int frameCount() {
    throw new UnsupportedOperationException("Not implemented: \"frameCount\" in " + getClass().getName());
  }

  @Override
  public List<StackFrame> frames() {
    throw new UnsupportedOperationException("Not implemented: \"frames\" in " + getClass().getName());
  }

  @Override
  public StackFrame frame(int i) {
    throw new UnsupportedOperationException("Not implemented: \"frame\" in " + getClass().getName());
  }

  @Override
  public List<StackFrame> frames(int i,int i1) {
    throw new UnsupportedOperationException("Not implemented: \"frames\" in " + getClass().getName());
  }

  @Override
  public List<ObjectReference> ownedMonitors() {
    throw new UnsupportedOperationException("Not implemented: \"ownedMonitors\" in " + getClass().getName());
  }

  @Override
  public List/*<MonitorInfo>*/ ownedMonitorsAndFrames() {
    throw new UnsupportedOperationException("Not implemented: \"ownedMonitorsAndFrames\" in " + getClass().getName());
  }

  @Override
  public ObjectReference currentContendedMonitor() {
    throw new UnsupportedOperationException("Not implemented: \"currentContendedMonitor\" in " + getClass().getName());
  }

  @Override
  public void popFrames(StackFrame stackFrame) {
    throw new UnsupportedOperationException("Not implemented: \"popFrames\" in " + getClass().getName());
  }

  @Override
  public void forceEarlyReturn(Value value) {
    throw new UnsupportedOperationException("Not implemented: \"forceEarlyReturn\" in " + getClass().getName());
  }
}
