/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.jdi;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ClassLoaderReference;

public interface StackFrameProxy extends ObjectReferenceProxy{
  StackFrame getStackFrame() throws EvaluateException;

  int getFrameIndex() throws EvaluateException ;

  VirtualMachineProxy getVirtualMachine();

  Location location() throws EvaluateException;

  ClassLoaderReference getClassLoader() throws EvaluateException;

  LocalVariableProxy visibleVariableByName(String name) throws EvaluateException;
}
