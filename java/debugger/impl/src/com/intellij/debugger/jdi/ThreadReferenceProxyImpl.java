/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
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

  public static final Comparator<ThreadReferenceProxyImpl> ourComparator = new Comparator<ThreadReferenceProxyImpl>() {
    @Override
    public int compare(ThreadReferenceProxyImpl th1, ThreadReferenceProxyImpl th2) {
      int res = Comparing.compare(th2.isSuspended(), th1.isSuspended());
      if (res == 0) {
        return th1.name().compareToIgnoreCase(th2.name());
      }
      return res;
    }
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
    catch (IllegalThreadStateException e) {
      return ThreadReference.THREAD_STATUS_ZOMBIE;
    }
    catch (ObjectCollectedException ignored) {
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
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InternalException e) {
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
    catch (ObjectCollectedException ignored) {
      return null;
    }
    catch (IllegalThreadStateException ignored) {
      return null;
    }
  }

  public void popFrames(StackFrameProxyImpl stackFrame) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      getThreadReference().popFrames(stackFrame.getStackFrame());
    }
    catch (InvalidStackFrameException ignored) {
    }
    catch (ObjectCollectedException ignored) {
    }
    catch (InternalException e) {
      if (e.errorCode() == 32) {
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

  public boolean isSuspended() throws ObjectCollectedException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      return getThreadReference().isSuspended();
    }
    catch (IllegalThreadStateException e) {
      // must be zombie thread
      LOG.info(e);
      return false;
    }
  }

  public boolean isAtBreakpoint() {
    try {
      return getThreadReference().isAtBreakpoint();
    } catch (InternalException e) {
      LOG.info(e);
    }
    return false;
  }
}
