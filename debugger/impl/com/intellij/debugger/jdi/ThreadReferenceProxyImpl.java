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
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
      return th1.name().compareToIgnoreCase(th2.name());
    }
  };

  public ThreadReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadReference threadReference) {
    super(virtualMachineProxy, threadReference);
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
        myName = getThreadReference().name();
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
    try {
      return getThreadReference().suspendCount();
    }
    catch (ObjectCollectedException e) {
      return 0;
    }
  }

  public void suspend() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().suspend();
    }
    catch (ObjectCollectedException ignored) {
    }
    clearCaches();
  }

  public @NonNls String toString() {
    //noinspection HardCodedStringLiteral
    @NonNls String threadRefString;
    try {
      threadRefString = getThreadReference().toString() ;
    }
    catch (ObjectCollectedException e) {
      threadRefString = "[thread collected]";
    }
    return "ThreadReferenceProxyImpl: " + threadRefString + " " + super.toString();
  }

  public void resume() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //JDI clears all caches on thread resume !!
    final ThreadReference threadRef = getThreadReference();
    if(LOG.isDebugEnabled()) {
      LOG.debug("before resume" + threadRef);
    }
    getVirtualMachineProxy().clearCaches();
    try {
      threadRef.resume();
    }
    catch (ObjectCollectedException ignored) {
    }
  }

  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myFrames = null;
    myFrameCount = -1;
    super.clearCaches();
  }

  public int status() {
    try {
      return getThreadReference().status();
    }
    catch (ObjectCollectedException e) {
      return ThreadReference.THREAD_STATUS_ZOMBIE;
    }
  }

  public ThreadGroupReferenceProxyImpl threadGroupProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if(myThreadGroupProxy == null) {
      ThreadGroupReference threadGroupRef;
      try {
        threadGroupRef = getThreadReference().threadGroup();
      }
      catch (ObjectCollectedException e) {
        threadGroupRef = null;
      }
      myThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(threadGroupRef);
    }
    return myThreadGroupProxy;
  }

  public int frameCount() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myFrameCount == -1) {
      final ThreadReference threadReference = getThreadReference();
      try {
        myFrameCount = threadReference.frameCount();
      }
      catch(ObjectCollectedException e) {
        myFrameCount = 0;
      }
      catch (IncompatibleThreadStateException e) {
        if (!threadReference.isSuspended()) {
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
    final ThreadReference threadRef = getThreadReference();
    try {
      LOG.assertTrue(threadRef.isSuspended());
      checkValid();

      if(myFrames == null) {
        checkFrames(threadRef);
  
        myFrames = new ArrayList<StackFrameProxyImpl>(frameCount());
        for (ListIterator<StackFrameProxyImpl> iterator = myFramesFromBottom.listIterator(frameCount()); iterator.hasPrevious();) {
          StackFrameProxyImpl stackFrameProxy = iterator.previous();
          myFrames.add(stackFrameProxy);
        }
      }
    }
    catch (ObjectCollectedException e) {
      return Collections.emptyList();
    }
    return myFrames;
  }

  private void checkFrames(@NotNull final ThreadReference threadRef) throws EvaluateException {
    if (myFramesFromBottom.size() < frameCount()) {
      int count = frameCount();
      List<StackFrame> frames = null;
      try {
        frames = threadRef.frames(0, count - myFramesFromBottom.size());
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
    try {
      if(!threadReference.isSuspended()) {
        return null;
      }
      checkFrames(threadReference);
      return myFramesFromBottom.get(frameCount() - i  - 1);
    }
    catch (ObjectCollectedException e) {
      return null;
    }
  }

  public void popFrames(StackFrameProxyImpl stackFrame) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().popFrames(stackFrame.getStackFrame());
    }
    catch (ObjectCollectedException ignored) {
    }
    catch (InternalException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    finally {
      clearCaches();
      getVirtualMachineProxy().clearCaches();
    }
  }

  public boolean isSuspended() throws ObjectCollectedException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getThreadReference().isSuspended();
  }
}
