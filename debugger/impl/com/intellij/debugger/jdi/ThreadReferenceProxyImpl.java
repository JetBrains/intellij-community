/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public final class ThreadReferenceProxyImpl extends ObjectReferenceProxyImpl implements ThreadReferenceProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.ThreadReferenceProxyImpl");
  // cached data
  private String myName;
  private int                       myFrameCount = -1;
  // stackframes, 0 - bottom
  private List<StackFrameProxyImpl> myFramesFromBottom = new ArrayList<StackFrameProxyImpl>();
  //cache build on the base of myFramesFromBottom 0 - top, initially nothing is cached
  private List<StackFrameProxyImpl> myFrames = null;

  private ThreadGroupReferenceProxyImpl myThreadGroupProxy;

  public static Comparator<ThreadReferenceProxyImpl> ourComparator = new Comparator<ThreadReferenceProxyImpl>() {
    public int compare(ThreadReferenceProxyImpl th1, ThreadReferenceProxyImpl th2) {
      return th1.name().compareTo(th2.name());
    }
  };

  public ThreadReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadReference threadReference) {
    super(virtualMachineProxy, threadReference);
    LOG.assertTrue(threadReference != null);
  }

  public ThreadReference getThreadReference() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return (ThreadReference)getObjectReference();
  }

  public VirtualMachineProxyImpl getVirtualMachine() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return (VirtualMachineProxyImpl) myTimer;
  }

  public String name() {
    checkValid();
    if (myName == null) {
      try {
        final ThreadReference threadReference = getThreadReference();
        // when thread ref is collected, the returned reference will be null
        myName = threadReference == null? "" : threadReference.name();
      }
      catch (ObjectCollectedException e) {
        myName = "";
      }
    }
    return myName;
  }

  public int getSuspendCount() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //LOG.assertTrue((mySuspendCount > 0) == suspends());
    return getThreadReference().suspendCount();
  }

  public void suspend() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference threadReference = getThreadReference();
    if (threadReference != null) {
      threadReference.suspend();
    }
    clearCaches();
  }

  public @NonNls String toString() {
    return "ThreadReferenceProxyImpl: " + getThreadReference().toString() + " " + super.toString();
  }

  public void resume() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //JDI clears all caches on thread resume !!
    if(LOG.isDebugEnabled()) {
      LOG.debug("before resume" + getThreadReference());
    }
    getVirtualMachineProxy().clearCaches();
    getThreadReference().resume();
  }

  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myFrames = null;
    myFrameCount = -1;
    super.clearCaches();
  }

  public int suspendCount() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getThreadReference().suspendCount();
  }

  public int status() {
    return getThreadReference().status();
  }

  public ThreadGroupReferenceProxyImpl threadGroupProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if(myThreadGroupProxy == null) {
      myThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(getThreadReference().threadGroup());
    }
    return myThreadGroupProxy;
  }

  public int frameCount() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myFrameCount == -1) {
      try {
        final ThreadReference threadReference = getThreadReference();
        myFrameCount = threadReference != null? threadReference.frameCount() : 0;
      }
      catch (IncompatibleThreadStateException e) {
        if (!isSuspended()) {
          // give up because it seems to be really resumed
          throw EvaluateExceptionUtil.createEvaluateException(e);
        }
        else {
          // JDI bug: although isSuspended() == true, frameCount() may throw IncompatibleTharedStateException
          // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4783403
          // unfortunately, impossible to get this information at the moment, so assume the frame count is null
          myFrameCount = 0;
        }
      }
    }
    return myFrameCount;
  }

  public List<StackFrameProxyImpl> frames() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(getThreadReference().isSuspended());
    checkValid();

    if(myFrames == null) {
      checkFrames();

      myFrames = new ArrayList<StackFrameProxyImpl>(frameCount());
      for (ListIterator<StackFrameProxyImpl> iterator = myFramesFromBottom.listIterator(frameCount()); iterator.hasPrevious();) {
        StackFrameProxyImpl stackFrameProxy = iterator.previous();
        myFrames.add(stackFrameProxy);
      }
    }
    return myFrames;
  }

  private void checkFrames() throws EvaluateException {
    if (myFramesFromBottom.size() < frameCount()) {
      int count = frameCount();
      List<StackFrame> frames = null;
      try {
        frames = getThreadReference().frames(0, count - myFramesFromBottom.size());
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InternalException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }

      int index = myFramesFromBottom.size() + 1;
      for (ListIterator<StackFrame> iterator = frames.listIterator(count - myFramesFromBottom.size()); iterator.hasPrevious();) {
        StackFrame stackFrame = iterator.previous();
        myFramesFromBottom.add(new StackFrameProxyImpl(this, stackFrame, index));
        index++;
      }
    }
  }

  public StackFrameProxyImpl frame(int i) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference threadReference = getThreadReference();
    if(threadReference == null || !threadReference.isSuspended()) {
      return null;
    }
    checkFrames();
    return myFramesFromBottom.get(frameCount() - i  - 1);
  }

  public void popFrames(StackFrameProxyImpl stackFrame) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().popFrames(stackFrame.getStackFrame());
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    finally {
      clearCaches();
      getVirtualMachineProxy().clearCaches();
    }
  }

  public boolean isSuspended() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference thread = getThreadReference();
    return thread != null && thread.isSuspended();
  }
}
