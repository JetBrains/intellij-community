/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public int hashCode() {
    return 31 * myFrame.hashCode() + myVariableName.hashCode();
  }

  public boolean equals(Object o) {
    if (o instanceof LocalVariableProxyImpl) {
      LocalVariableProxyImpl proxy = (LocalVariableProxyImpl)o;
      return Comparing.equal(proxy.myFrame, myFrame) && myVariableName.equals(proxy.myVariableName);
    }
    return false;
  }

  public String name() {
    return myVariableName;
  }

  public String typeName() {
    return myTypeName;
  }
}
