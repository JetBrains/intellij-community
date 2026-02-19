// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.openapi.util.Comparing;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Type;

public class LocalVariableProxyImpl extends JdiProxy implements LocalVariableProxy {
  private final StackFrameProxyImpl myFrame;
  private final String myVariableName;
  private final String myTypeName;

  private LocalVariable myVariable;
  private Type myVariableType;

  public LocalVariableProxyImpl(StackFrameProxyImpl frame, LocalVariable variable) {
    super(frame.myTimer);
    myFrame = frame;
    myVariableName = variable.name();
    myTypeName = variable.typeName();
    myVariable = variable;
  }

  @Override
  protected void clearCaches() {
    myVariable = null;
    myVariableType = null;
  }

  public LocalVariable getVariable() throws EvaluateException {
    checkValid();
    if (myVariable == null) {
      myVariable = myFrame.visibleVariableByNameInt(myVariableName);
      if (myVariable == null) {
        //myFrame is not this variable's frame
        throw EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException());
      }
    }

    return myVariable;
  }

  public Type getType() throws EvaluateException, ClassNotLoadedException {
    if (myVariableType == null) {
      myVariableType = getVariable().type();
    }
    return myVariableType;
  }

  public StackFrameProxyImpl getFrame() {
    return myFrame;
  }

  @Override
  public int hashCode() {
    return 31 * myFrame.hashCode() + myVariableName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LocalVariableProxyImpl proxy &&
           Comparing.equal(proxy.myFrame, myFrame) &&
           myVariableName.equals(proxy.myVariableName);
  }

  public String name() {
    return myVariableName;
  }

  public String typeName() {
    return myTypeName;
  }

  @Override
  public String toString() {
    return myVariableName;
  }
}
