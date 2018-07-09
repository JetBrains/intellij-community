/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import com.sun.jdi.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackFrameProxyImpl extends JdiProxy implements StackFrameProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.StackFrameProxyImpl");
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final int myFrameFromBottomIndex; // 1-based

  //caches
  private int myFrameIndex = -1;
  private StackFrame myStackFrame;
  private ObjectReference myThisReference;
  private ClassLoaderReference myClassLoader;
  private ThreeState myIsObsolete = ThreeState.UNSURE;
  private Map<LocalVariable, Value> myAllValues;

  public StackFrameProxyImpl(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex /* 1-based */) {
    super(threadProxy.getVirtualMachine());
    myThreadProxy = threadProxy;
    myFrameFromBottomIndex = fromBottomIndex;
    myStackFrame = frame;
  }

  public boolean isObsolete() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myIsObsolete != ThreeState.UNSURE) {
      return myIsObsolete.toBoolean();
    }
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Method method = DebuggerUtilsEx.getMethod(location());
        boolean isObsolete = (getVirtualMachine().canRedefineClasses() && (method == null || method.isObsolete()));
        myIsObsolete = ThreeState.fromBoolean(isObsolete);
        return isObsolete;
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
      catch (InternalException e) {
        if (e.errorCode() == JvmtiError.INVALID_METHODID) {
          myIsObsolete = ThreeState.YES;
          return true;
        }
        throw e;
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @Override
  public boolean isValid() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!super.isValid()) {
      return false;
    }
    try {
      if (myStackFrame != null) {
        myStackFrame.location(); //extra check if jdi frame is valid
      }
      return true;
    } catch (InvalidStackFrameException e) {
      return false;
    }
  }

  @Override
  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("caches cleared " + super.toString());
    }
    myFrameIndex = -1;
    myStackFrame = null;
    myIsObsolete = ThreeState.UNSURE;
    myThisReference = null;
    myClassLoader = null;
    myAllValues = null;
  }

  /**
   * Use with caution. Better access stackframe data through the Proxy's methods
   */

  @Override
  public StackFrame getStackFrame() throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    checkValid();

    if (myStackFrame == null) {
      try {
        final ThreadReference threadRef = myThreadProxy.getThreadReference();
        myStackFrame = threadRef.frame(getFrameIndex());
      }
      catch (IndexOutOfBoundsException e) {
        throw new EvaluateException(e.getMessage(), e);
      }
      catch (ObjectCollectedException ignored) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.thread.collected"));
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }

    return myStackFrame;
  }

  @Override
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

  @NotNull
  @Override
  public VirtualMachineProxyImpl getVirtualMachine() {
    return (VirtualMachineProxyImpl) myTimer;
  }

  @Override
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
    throw new EvaluateException(error.getMessage(), error);
  }

  @NotNull
  @Override
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
        catch (InvalidStackFrameException ignored) {
          clearCaches();
        }
      }
    }
    catch (InternalException e) {
      // suppress some internal errors caused by bugs in specific JDI implementations
      if (e.errorCode() != JvmtiError.INVALID_METHODID && e.errorCode() != JvmtiError.INVALID_SLOT) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      else {
        LOG.info("Exception while getting this object", e);
      }
    }
    catch (IllegalArgumentException e) {
        LOG.info("Exception while getting this object", e);
    }
    return myThisReference;
  }

  @NotNull
  public List<LocalVariableProxyImpl> visibleVariables() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    RuntimeException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final List<LocalVariable> list = getStackFrame().visibleVariables();
        final List<LocalVariableProxyImpl> locals = new ArrayList<>(list.size());
        for (LocalVariable localVariable : list) {
          LOG.assertTrue(localVariable != null);
          locals.add(new LocalVariableProxyImpl(this, localVariable));
        }
        return locals;
      }
      catch (InvalidStackFrameException | IllegalArgumentException e) {
        error = e;
        clearCaches();
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @Override
  public LocalVariableProxyImpl visibleVariableByName(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? new LocalVariableProxyImpl(this, variable) : null;
  }

  @Nullable
  public Value visibleValueByName(@NotNull String name) throws EvaluateException {
    LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? getValue(new LocalVariableProxyImpl(this, variable)) : null;
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
      catch (InvalidStackFrameException | AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  public Value getValue(LocalVariableProxyImpl localVariable) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Map<LocalVariable, Value> values = getAllValues();
        LocalVariable variable = localVariable.getVariable();
        if (values.containsKey(variable)) {
          return values.get(variable);
        }
        else { // try direct get
          return getStackFrame().getValue(variable);
        }
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
      catch (InconsistentDebugInfoException ignored) {
        clearCaches();
        throw EvaluateExceptionUtil.INCONSISTEND_DEBUG_INFO;
      }
      catch (InternalException e) {
        if (e.errorCode() == JvmtiError.INVALID_SLOT || e.errorCode() == JvmtiError.ABSENT_INFORMATION) {
          throw new EvaluateException(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()), e);
        }
        else throw e;
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @NotNull
  public List<Value> getArgumentValues() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final StackFrame stackFrame = getStackFrame();
        return stackFrame != null? stackFrame.getArgumentValues() : Collections.emptyList();
      }
      catch (InternalException e) {
        // From Oracle's forums:
        // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was detected at the lower JPDA levels,
        // typically a native frame.
        if (e.errorCode() == JvmtiError.OPAQUE_FRAME /*opaque frame JDI bug*/ ) {
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
    throw new EvaluateException(error.getMessage(), error);
  }

  private Map<LocalVariable, Value> getAllValues() throws EvaluateException{
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myAllValues == null) {
      try {
        StackFrame stackFrame = getStackFrame();
        myAllValues = new THashMap<>(stackFrame.getValues(stackFrame.visibleVariables()));
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InternalException e) {
        // extra logging for IDEA-141270
        if (e.errorCode() == JvmtiError.INVALID_SLOT || e.errorCode() == JvmtiError.ABSENT_INFORMATION) {
          LOG.info(e);
          myAllValues = new THashMap<>();
        }
        else throw e;
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
    throw new EvaluateException(error.getMessage(), error);
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
    catch (IllegalArgumentException ignored) {
      // can be thrown if frame's method is different than variable's method
      return false;
    }
  }

  @Override
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

