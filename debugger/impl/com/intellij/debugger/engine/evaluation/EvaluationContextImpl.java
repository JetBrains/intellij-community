package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Aug 28, 2003
 * Time: 2:02:29 PM
 */
public final class EvaluationContextImpl implements EvaluationContext{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.EvaluationContextImpl");

  private final Value myThisObject;
  private final SuspendContextImpl mySuspendContext;
  private final StackFrameProxyImpl myFrameProxy;
  private boolean myAutoLoadClasses = true;
  
  public EvaluationContextImpl(SuspendContextImpl suspendContext, StackFrameProxyImpl frameProxy, Value thisObject) {
    myThisObject = thisObject;
    myFrameProxy = frameProxy;
    mySuspendContext = suspendContext;
    LOG.assertTrue(suspendContext != null);
  }

  public Value getThisObject() {
    return myThisObject;
  }

  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  public StackFrameProxyImpl getFrameProxy() {
    return myFrameProxy;
  }

  public DebugProcessImpl getDebugProcess() {
    return getSuspendContext().getDebugProcess();
  }

  public Project getProject() {
    DebugProcessImpl debugProcess = getDebugProcess();
    return debugProcess != null ? debugProcess.getProject() : null;
  }

  public EvaluationContextImpl createEvaluationContext(Value value) {
    final EvaluationContextImpl copy = new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), value);
    copy.setAutoLoadClasses(myAutoLoadClasses);
    return copy;
  }

  public ClassLoaderReference getClassLoader() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myFrameProxy != null ? myFrameProxy.getClassLoader() : null;
  }

  public boolean isAutoLoadClasses() {
    return myAutoLoadClasses;
  }

  public void setAutoLoadClasses(final boolean autoLoadClasses) {
    myAutoLoadClasses = autoLoadClasses;
  }
}
