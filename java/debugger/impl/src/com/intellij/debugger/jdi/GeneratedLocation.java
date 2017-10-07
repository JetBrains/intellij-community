// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class GeneratedLocation implements Location {
  private final DebugProcessImpl myDebugProcess;
  private final int myLineNumber;
  private final ReferenceType myReferenceType;
  private final Method myMethod;

  public GeneratedLocation(DebugProcessImpl debugProcess, String className, String methodName, int lineNumber) {
    myDebugProcess = debugProcess;
    myLineNumber = lineNumber;
    myReferenceType = ContainerUtil.getFirstItem(myDebugProcess.getVirtualMachineProxy().classesByName(className));
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
    throw new IllegalStateException();
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
    return myDebugProcess.getVirtualMachineProxy().getVirtualMachine();
  }

  @Override
  public int compareTo(@NotNull Location o) {
    throw new IllegalStateException();
  }
}
