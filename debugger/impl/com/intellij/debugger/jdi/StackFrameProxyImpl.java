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

import java.util.*;

public class StackFrameProxyImpl extends JdiProxy implements StackFrameProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.StackFrameProxyImpl");
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final int myFrameFromBottomIndex; // 1-based

  //caches
  private int myFrameIndex = -1;
  private StackFrame myStackFrame;
  private ObjectReference myThisReference;
  private Location myLocation;
  private ClassLoaderReference myClassLoader;
  private Boolean myIsObsolete = null;
  private Map<LocalVariable,Value> myAllValues;

  public StackFrameProxyImpl(ThreadReferenceProxyImpl threadProxy, StackFrame frame, int fromBottomIndex /* 1-based */) {
    super(threadProxy.getVirtualMachine());
    myThreadProxy = threadProxy;
    myFrameFromBottomIndex = fromBottomIndex;
    myStackFrame = frame;
    LOG.assertTrue(frame != null);
  }

  public boolean isObsolete() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myIsObsolete == null) {
      try {
        boolean isObsolete = (getVirtualMachine().canRedefineClasses() && location().method().isObsolete());
        //boolean isObsolete = (getVirtualMachine().versionHigher("1.4") && location().method().isObsolete());
        myIsObsolete = isObsolete? Boolean.TRUE : Boolean.FALSE;
      }
      catch (InvalidStackFrameException e) {
        clearCaches();
        return isObsolete();
      }
    }
    return myIsObsolete.booleanValue();
  }

  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //DebuggerManagerThreadImpl.assertIsManagerThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("caches cleared " + super.toString());
    }
    myFrameIndex = -1;
    myStackFrame = null;
    myIsObsolete = null;
    myLocation      = null;
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
        if (threadRef == null) {
          throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.thread.collected"));
        }
        myStackFrame = threadRef.frame(getFrameIndex());
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
    checkValid();
    if(myLocation == null) {
      try {
        myLocation = getStackFrame().location();
      }
      catch (InvalidStackFrameException e) {
        clearCaches();
        return location();
      }
    }
    return myLocation;
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

  public ObjectReference thisObject() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    try {
      if(myThisReference == null) {
        myThisReference = getStackFrame().thisObject();
      }
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return thisObject();
    }
    catch (InternalException e) {
      if(e.errorCode() == 35/*bug in JDK 1.5 beta*/) {
        LOG.debug(e);
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      else {
        // supress some internal errors caused by bugs in specific JDI implementations
        if (e.errorCode() != 23/*bug in JDK 1.4*/) {
          throw e;
        }
      }
    }
    return myThisReference;
  }

  public List<LocalVariableProxyImpl> visibleVariables() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      List<LocalVariable> list = getStackFrame().visibleVariables();

      List<LocalVariableProxyImpl> locals = new ArrayList<LocalVariableProxyImpl>();
      for (Iterator<LocalVariable> iterator = list.iterator(); iterator.hasNext();) {
        LocalVariable localVariable = iterator.next();
        LOG.assertTrue(localVariable != null);
        locals.add(new LocalVariableProxyImpl(this, localVariable));
      }
      return locals;
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return visibleVariables();
    }
    catch (AbsentInformationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public LocalVariableProxyImpl visibleVariableByName(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? new LocalVariableProxyImpl(this, variable) : null;
  }

  protected LocalVariable visibleVariableByNameInt(String name) throws EvaluateException  {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      try {
        return getStackFrame().visibleVariableByName(name);
      }
      catch (InvalidStackFrameException e) {
        clearCaches();
        return getStackFrame().visibleVariableByName(name);
      }
    }
    catch (InvalidStackFrameException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (AbsentInformationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public Value getValue(LocalVariableProxyImpl localVariable) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      final Map<LocalVariable, Value> allValues = getAllValues();
      return allValues.get(localVariable.getVariable());
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      return getValue(localVariable);
    }
  }

  private Map<LocalVariable, Value> getAllValues() throws EvaluateException{
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myAllValues == null) {
      try {
        final StackFrame stackFrame = getStackFrame();
        final Map<LocalVariable, Value> values = stackFrame.getValues(stackFrame.visibleVariables());
        myAllValues = new HashMap<LocalVariable, Value>(values.size());
        for (Iterator<LocalVariable> it = values.keySet().iterator(); it.hasNext();) {
          final LocalVariable variable = it.next();
          final Value value = values.get(variable);
          myAllValues.put(variable, (value instanceof ObjectReference)? (ObjectReference)value : value);
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

  public void setValue(LocalVariableProxyImpl localVariable, Value value) throws EvaluateException,
                                                                                 ClassNotLoadedException,
                                                                                 InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      final LocalVariable variable = localVariable.getVariable();
      final StackFrame stackFrame = getStackFrame();
      stackFrame.setValue(variable, (value instanceof ObjectReference)? ((ObjectReference)value) : value);
      if (myAllValues != null) {
        // update cached data if any
        // re-read the value just set from the stackframe to be 100% sure
        myAllValues.put(variable, stackFrame.getValue(variable));
      }
    }
    catch (InvalidStackFrameException e) {
      clearCaches();
      setValue(localVariable, value);
    }
  }

  public int hashCode() {
    return myThreadProxy.hashCode() + myFrameFromBottomIndex;
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

}

