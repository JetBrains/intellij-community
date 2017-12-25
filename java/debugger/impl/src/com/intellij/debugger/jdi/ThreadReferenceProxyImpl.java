/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ThreadReferenceProxyImpl extends ObjectReferenceProxyImpl implements ThreadReferenceProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.ThreadReferenceProxyImpl");
  // cached data
  private String myName;
  private int                       myFrameCount = -1;
  // stack frames, 0 - bottom
  private final LinkedList<StackFrameProxyImpl> myFramesFromBottom = new LinkedList<>();
  //cache build on the base of myFramesFromBottom 0 - top, initially nothing is cached
  private List<StackFrameProxyImpl> myFrames = null;

  private ThreadGroupReferenceProxyImpl myThreadGroupProxy;

  private ThreeState myResumeOnHotSwap = ThreeState.UNSURE;

  public static final Comparator<ThreadReferenceProxyImpl> ourComparator = (th1, th2) -> {
    int res = Comparing.compare(th2.isSuspended(), th1.isSuspended());
    if (res == 0) {
      return th1.name().compareToIgnoreCase(th2.name());
    }
    return res;
  };

  public ThreadReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadReference threadReference) {
    super(virtualMachineProxy, threadReference);
  }

  @Override
  public ThreadReference getThreadReference() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return (ThreadReference)getObjectReference();
  }

  @NotNull
  @Override
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
      catch (ObjectCollectedException ignored) {
        myName = "";
      }
      catch (IllegalThreadStateException ignored) {
        myName = "zombie";
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
    catch (ObjectCollectedException ignored) {
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

  @NonNls
  public String toString() {
    try {
      return name() + ": " + DebuggerUtilsEx.getThreadStatusText(status());
    }
    catch (ObjectCollectedException ignored) {
      return "[thread collected]";
    }
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

  @Override
  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myName = null;
    myFrames = null;
    myFrameCount = -1;
    super.clearCaches();
  }

  public int status() {
    try {
      return getThreadReference().status();
    }
    catch (IllegalThreadStateException | ObjectCollectedException e) {
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
      catch (ObjectCollectedException ignored) {
        threadGroupRef = null;
      }
      myThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(threadGroupRef);
    }
    return myThreadGroupProxy;
  }

  @Override
  public int frameCount() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myFrameCount == -1) {
      final ThreadReference threadReference = getThreadReference();
      try {
        myFrameCount = threadReference.frameCount();
      }
      catch(ObjectCollectedException ignored) {
        myFrameCount = 0;
      }
      catch (IncompatibleThreadStateException e) {
        final boolean isSuspended;
        try {
          isSuspended = threadReference.isSuspended();
        }
        catch (Throwable ignored) {
          // unable to determine whether the thread is actually suspended, so propagating original exception
          throw EvaluateExceptionUtil.createEvaluateException(e);
        }
        if (!isSuspended) {
          // give up because it seems to be really resumed
          throw EvaluateExceptionUtil.createEvaluateException(e);
        }
        else {
          // JDI bug: although isSuspended() == true, frameCount() may throw IncompatibleThreadStateException
          // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4783403
          // unfortunately, impossible to get this information at the moment, so assume the frame count is null
          myFrameCount = 0;
        }
      }
      catch (InternalException e) {
        LOG.info(e);
        myFrameCount = 0;
      }
    }
    return myFrameCount;
  }

  /**
   * Same as frames(), but always force full frames refresh if not cached,
   * this is useful when you need all frames but do not plan to invoke anything
   * as only one request is sent
   */
  @NotNull
  public List<StackFrameProxyImpl> forceFrames() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference threadRef = getThreadReference();
    try {
      //LOG.assertTrue(threadRef.isSuspended());
      checkValid();

      if (myFrames == null) {
        try {
          List<StackFrame> frames = threadRef.frames();
          myFrameCount = frames.size();
          myFrames = new ArrayList<>(myFrameCount);
          myFramesFromBottom.clear();
          int idx = 0;
          for (StackFrame frame : frames) {
            StackFrameProxyImpl frameProxy = new StackFrameProxyImpl(this, frame, myFrameCount - idx);
            myFrames.add(frameProxy);
            myFramesFromBottom.addFirst(frameProxy);
            idx++;
          }
        }
        catch (IncompatibleThreadStateException | InternalException e) {
          throw EvaluateExceptionUtil.createEvaluateException(e);
        }
      }
    }
    catch (ObjectCollectedException ignored) {
      return Collections.emptyList();
    }
    return myFrames;
  }

  @NotNull
  public List<StackFrameProxyImpl> frames() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference threadRef = getThreadReference();
    try {
      //LOG.assertTrue(threadRef.isSuspended());
      checkValid();

      if (myFrames == null) {
        checkFrames(threadRef);

        myFrames = ContainerUtil.reverse(new ArrayList<>(myFramesFromBottom.subList(0, frameCount())));
      }
    }
    catch (ObjectCollectedException ignored) {
      return Collections.emptyList();
    }
    return myFrames;
  }

  private void checkFrames(@NotNull final ThreadReference threadRef) throws EvaluateException {
    int frameCount = frameCount();
    if (myFramesFromBottom.size() < frameCount) {
      List<StackFrame> frames;
      try {
        frames = threadRef.frames(0, frameCount - myFramesFromBottom.size());
      }
      catch (IncompatibleThreadStateException | InternalException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }

      int index = myFramesFromBottom.size() + 1;
      for (ListIterator<StackFrame> iterator = frames.listIterator(frameCount - myFramesFromBottom.size()); iterator.hasPrevious();) {
        myFramesFromBottom.add(new StackFrameProxyImpl(this, iterator.previous(), index));
        index++;
      }
    }
    else { // avoid leaking frames
      while (myFramesFromBottom.size() > frameCount) {
        myFramesFromBottom.removeLast();
      }
    }
  }

  @Override
  public StackFrameProxyImpl frame(int i) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ThreadReference threadReference = getThreadReference();
    try {
      if(!threadReference.isSuspended()) {
        return null;
      }
      checkFrames(threadReference);
      final int frameCount = frameCount();
      if (frameCount == 0) {
        return null;
      }
      return myFramesFromBottom.get(frameCount - i  - 1);
    }
    catch (ObjectCollectedException | IllegalThreadStateException ignored) {
      return null;
    }
  }

  public void popFrames(StackFrameProxyImpl stackFrame) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().popFrames(stackFrame.getStackFrame());
    }
    catch (InvalidStackFrameException | ObjectCollectedException ignored) {
    }
    catch (InternalException e) {
      if (e.errorCode() == JvmtiError.OPAQUE_FRAME) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("drop.frame.error.no.information"));
      }
      else throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    finally {
      clearCaches();
      getVirtualMachineProxy().clearCaches();
    }
  }

  public void forceEarlyReturn(Value value) throws ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().forceEarlyReturn(value);
    }
    finally {
      clearCaches();
      getVirtualMachineProxy().clearCaches();
    }
  }

  public void stop(ObjectReference exception) throws InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().stop(exception);
    }
    finally {
      clearCaches();
      getVirtualMachineProxy().clearCaches();
    }
  }

  public boolean isSuspended() throws ObjectCollectedException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      return getThreadReference().isSuspended();
    }
    catch (IllegalThreadStateException e) {
      // must be zombie thread
      LOG.info(e);
    } catch (ObjectCollectedException ignored) {
    }

    return false;
  }

  public boolean isAtBreakpoint() {
    try {
      return getThreadReference().isAtBreakpoint();
    } catch (InternalException e) {
      LOG.info(e);
    } catch (ObjectCollectedException ignored) {
    }
    return false;
  }

  public boolean isResumeOnHotSwap() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myResumeOnHotSwap == ThreeState.UNSURE) {
      myResumeOnHotSwap = ThreeState.fromBoolean(name().startsWith("YJPAgent-"));
    }
    return myResumeOnHotSwap.toBoolean();
  }
}
