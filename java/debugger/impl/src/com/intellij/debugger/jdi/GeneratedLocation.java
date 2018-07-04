// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author egor
 */
public class GeneratedLocation implements Location {
  private final VirtualMachine myVirtualMachine;
  private final int myLineNumber;
  private final ReferenceType myReferenceType;
  private final Method myMethod;

  public GeneratedLocation(DebugProcessImpl debugProcess, ReferenceType type, String methodName, int lineNumber) {
    myVirtualMachine = debugProcess.getVirtualMachineProxy().getVirtualMachine();
    myLineNumber = lineNumber;
    myReferenceType = type;
    myMethod = ContainerUtil.getFirstItem(myReferenceType.methodsByName(methodName));
  }

  @Override
  public ReferenceType declaringType() {
    return myReferenceType;
  }

  @Override
  public Method method() {
    return myMethod;
  }

  @Override
  public long codeIndex() {
    return -2; // to be never equal to any LocationImpl
  }

  @Override
  public String sourceName() throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public String sourceName(String s) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public String sourcePath() throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public String sourcePath(String s) throws AbsentInformationException {
    throw new AbsentInformationException();
  }

  @Override
  public int lineNumber() {
    return myLineNumber;
  }

  @Override
  public int lineNumber(String s) {
    return myLineNumber;
  }

  @Override
  public VirtualMachine virtualMachine() {
    return myVirtualMachine;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GeneratedLocation location = (GeneratedLocation)o;
    return Objects.equals(myMethod, location.myMethod) &&
           myLineNumber == location.myLineNumber &&
           Objects.equals(myVirtualMachine, location.myVirtualMachine);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMethod, myLineNumber);
  }

  // Same as in LocationImpl
  @Override
  public int compareTo(@NotNull Location o) {
    int res = method().compareTo(o.method());
    if (res != 0) {
      return res;
    }
    return Long.compare(codeIndex(), o.codeIndex());
  }

  public String toString() {
    return myReferenceType.name() + ":" + myLineNumber;
  }
}
