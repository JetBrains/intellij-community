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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StackFrameProxyImpl extends JdiProxy implements StackFrameProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.StackFrameProxyImpl");
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final int myFrameFromBottomIndex; // 1-based

  //caches
  private int myFrameIndex = -1;
  private StackFrame myStackFrame;
  private ObjectReference myThisReference;
  private ClassLoaderReference myClassLoader;
  private Boolean myIsObsolete = null;
  private Map<LocalVariable,Value> myAllValues;

  public StackFrameProxyImpl(ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex /* 1-based */) {
    super(threadProxy.getVirtualMachine());
    myThreadProxy = threadProxy;
    myFrameFromBottomIndex = fromBottomIndex;
    myStackFrame = frame;
  }

  public boolean isObsolete() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myIsObsolete != null) {
      return myIsObsolete.booleanValue();
    }
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        boolean isObsolete = (getVirtualMachine().canRedefineClasses() && location().method().isObsolete());
        myIsObsolete = isObsolete? Boolean.TRUE : Boolean.FALSE;
        return isObsolete;
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
      catch (InternalException e) {
        if (e.errorCode() == 23 /*INVALID_METHODID accoeding to JDI sources*/) {
          myIsObsolete = Boolean.TRUE;
          return true;
        }
        throw e;
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return false;
  }

  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("caches cleared " + super.toString());
    }
    myFrameIndex = -1;
    myStackFrame = null;
    myIsObsolete = null;
    myThisReference = null;
    myClassLoader = null;
    myAllValues = null;
  }

  /**
   * Use with caution. Better access stackframe data through the Proxy's methods
   */

  public StackFrame getStackFrame() throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    checkValid();

    if(myStackFrame == null) {
      try {
        final ThreadReference threadRef = myThreadProxy.getThreadReference();
        myStackFrame = threadRef.frame(getFrameIndex());
      }
      catch (IndexOutOfBoundsException e) {
        throw new EvaluateException(e.getMessage(), e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.thread.collected"));
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }

    return myStackFrame;
  }

  public int getFrameIndex() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if(myFrameIndex == -1) {
      int count = myThreadProxy.frameCount();

      if(myFrameFromBottomIndex  > count) {
        throw EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException());
      }

      myFrameIndex = count - myFrameFromBottomIndex;
    }
    return myFrameIndex;
  }

//  public boolean isProxiedFrameValid() {
//    if (myStackFrame != null) {
//      try {
//        myStackFrame.thread();
//        return true;
//      }
//      catch (InvalidStackFrameException e) {
//      }
//    }
//    return false;
//  }

  public VirtualMachineProxyImpl getVirtualMachine() {
    return (VirtualMachineProxyImpl) myTimer;
  }

  public Location location() throws EvaluateException {
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return getStackFrame().location();
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return null;
  }

  public ThreadReferenceProxyImpl threadProxy() {
    return myThreadProxy;
  }

  public @NonNls String toString() {
    try {
      return "StackFrameProxyImpl: " + getStackFrame().toString();
    }
    catch (EvaluateException e) {
      return "StackFrameProxyImpl: " + e.getMessage() + "; frameFromBottom = " + myFrameFromBottomIndex + " threadName = " + threadProxy().name();
    }
  }

  @Nullable
  public ObjectReference thisObject() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          if(myThisReference == null) {
            myThisReference = getStackFrame().thisObject();
          }
          break;
        }
        catch (InvalidStackFrameException e) {
          clearCaches();
        }
      }
    }
    catch (InternalException e) {
      // supress some internal errors caused by bugs in specific JDI implementations
      if(e.errorCode() != 23) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    return myThisReference;
  }

  public List<LocalVariableProxyImpl> visibleVariables() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final List<LocalVariable> list = getStackFrame().visibleVariables();
        final List<LocalVariableProxyImpl> locals = new ArrayList<LocalVariableProxyImpl>(list.size());
        for (LocalVariable localVariable : list) {
          LOG.assertTrue(localVariable != null);
          locals.add(new LocalVariableProxyImpl(this, localVariable));
        }
        return locals;
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return Collections.emptyList();
  }

  public LocalVariableProxyImpl visibleVariableByName(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? new LocalVariableProxyImpl(this, variable) : null;
  }

  protected LocalVariable visibleVariableByNameInt(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        try {
          return getStackFrame().visibleVariableByName(name);
        }
        catch (InvalidStackFrameException e) {
          error = e;
          clearCaches();
        }
      }
      catch (InvalidStackFrameException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return null;
  }

  public Value getValue(LocalVariableProxyImpl localVariable) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final Map<LocalVariable, Value> allValues = getAllValues();
        return allValues.get(localVariable.getVariable());
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return null;
  }

  public List<Value> getArgumentValues() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final StackFrame stackFrame = getStackFrame();
        return stackFrame != null? stackFrame.getArgumentValues() : Collections.<Value>emptyList();
      }
      catch (InternalException e) {
        // From Oracle's forums:
        // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was detected at the lower JPDA levels,
        // typically a native frame.
        if (e.errorCode() == 32 /*opaque frame JDI bug*/ ) {
          return Collections.emptyList();
        }
        else {
          throw e;
        }
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
    return Collections.emptyList();
  }

  private Map<LocalVariable, Value> getAllValues() throws EvaluateException{
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myAllValues == null) {
      try {
        final StackFrame stackFrame = getStackFrame();
        final Map<LocalVariable, Value> values = stackFrame.getValues(stackFrame.visibleVariables());
        myAllValues = new HashMap<LocalVariable, Value>(values.size());
        for (final LocalVariable variable : values.keySet()) {
          final Value value = values.get(variable);
          myAllValues.put(variable, value);
        }
      }
      catch (InconsistentDebugInfoException e) {
        clearCaches();
        throw EvaluateExceptionUtil.INCONSISTEND_DEBUG_INFO;
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    return myAllValues;
  }

  public void setValue(LocalVariableProxyImpl localVariable, Value value) throws EvaluateException, ClassNotLoadedException, InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final LocalVariable variable = localVariable.getVariable();
        final StackFrame stackFrame = getStackFrame();
        stackFrame.setValue(variable, (value instanceof ObjectReference)? ((ObjectReference)value) : value);
        if (myAllValues != null) {
          // update cached data if any
          // re-read the value just set from the stackframe to be 100% sure
          myAllValues.put(variable, stackFrame.getValue(variable));
        }
        return;
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    if (error != null) {
      throw new EvaluateException(error.getMessage(), error);
    }
  }

  public int hashCode() {
    return 31 * myThreadProxy.hashCode() + myFrameFromBottomIndex;
  }


  public boolean equals(final Object obj) {
    if (!(obj instanceof StackFrameProxyImpl)) {
      return false;
    }
    StackFrameProxyImpl frameProxy = (StackFrameProxyImpl)obj;
    if(frameProxy == this)return true;

    return (myFrameFromBottomIndex == frameProxy.myFrameFromBottomIndex)  &&
           (myThreadProxy.equals(frameProxy.myThreadProxy));
  }

  public boolean isLocalVariableVisible(LocalVariableProxyImpl var) throws EvaluateException {
    try {
      return var.getVariable().isVisible(getStackFrame());
    }
    catch (IllegalArgumentException e) {
      // can be thrown if frame's method is different than variable's method
      return false;
    }
  }

  public ClassLoaderReference getClassLoader() throws EvaluateException {
    if(myClassLoader == null) {
      myClassLoader = location().declaringType().classLoader();
    }
    return myClassLoader;
  }

  public boolean isBottom() {
    return myFrameFromBottomIndex == 1;
  }

  public int getIndexFromBottom() {
    return myFrameFromBottomIndex;
  }
}

