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
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author lex
 */
public abstract class SuspendContextImpl implements SuspendContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendContextImpl");

  private final DebugProcessImpl myDebugProcess;
  private final int mySuspendPolicy;

  private ThreadReferenceProxyImpl myThread;
  boolean myIsVotedForResume = true;

  protected int myVotesToVote;
  protected Set<ThreadReferenceProxyImpl> myResumedThreads;

  private final EventSet myEventSet;
  private volatile boolean  myIsResumed;

  public ConcurrentLinkedQueue<SuspendContextCommandImpl> myPostponedCommands = new ConcurrentLinkedQueue<SuspendContextCommandImpl>();
  public volatile boolean  myInProgress;
  private final HashSet<ObjectReference>       myKeptReferences = new HashSet<ObjectReference>();
  private EvaluationContextImpl          myEvaluationContext = null;

  SuspendContextImpl(@NotNull DebugProcessImpl debugProcess, int suspendPolicy, int eventVotes, EventSet set) {
    myDebugProcess = debugProcess;
    mySuspendPolicy = suspendPolicy;
    myVotesToVote = eventVotes;
    myEventSet = set;
  }

  public void setThread(ThreadReference thread) {
    assertNotResumed();
    ThreadReferenceProxyImpl threadProxy = myDebugProcess.getVirtualMachineProxy().getThreadReferenceProxy(thread);
    LOG.assertTrue(myThread == null || myThread == threadProxy);
    myThread = threadProxy;
  }

  protected abstract void resumeImpl();

  protected void resume(){
    assertNotResumed();
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        for (ObjectReference objectReference : myKeptReferences) {
          try {
            objectReference.enableCollection();
          }
          catch (UnsupportedOperationException e) {
            // ignore: some J2ME implementations does not provide this operation
          }
        }
        myKeptReferences.clear();
      }

      for(SuspendContextCommandImpl cmd = myPostponedCommands.poll(); cmd != null; cmd = myPostponedCommands.poll()) {
        cmd.notifyCancelled();
      }

      resumeImpl();
    }
    finally {
      myIsResumed = true;
    }
  }

  private void assertNotResumed() {
    if (myIsResumed) {
      if (myDebugProcess.isAttached()) {
        LOG.assertTrue(false, "Cannot access SuspendContext. SuspendContext is resumed.");
      }
    }
  }


  public EventSet getEventSet() {
    assertNotResumed();
    return myEventSet;
  }

  public DebugProcessImpl getDebugProcess() {
    assertNotResumed();
    return myDebugProcess;
  }

  public StackFrameProxyImpl getFrameProxy() {
    assertNotResumed();
    try {
      return myThread != null && myThread.frameCount() > 0 ? myThread.frame(0) : null;
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public ThreadReferenceProxyImpl getThread() {
    return myThread;
  }

  public int getSuspendPolicy() {
    assertNotResumed();
    return mySuspendPolicy;
  }

  public void doNotResumeHack() {
    assertNotResumed();
    myVotesToVote = 1000000000;
  }

  public boolean isExplicitlyResumed(ThreadReferenceProxyImpl thread) {
    return myResumedThreads != null ? myResumedThreads.contains(thread) : false;
  }

  public boolean suspends(ThreadReferenceProxyImpl thread) {
    assertNotResumed();
    if(isEvaluating()) {
      return false;
    }
    switch(getSuspendPolicy()) {
      case EventRequest.SUSPEND_ALL:
        return !isExplicitlyResumed(thread);
      case EventRequest.SUSPEND_EVENT_THREAD:
        return thread == getThread();
    }
    return false;
  }

  public boolean isEvaluating() {
    assertNotResumed();
    return myEvaluationContext != null;
  }

  public EvaluationContextImpl getEvaluationContext() {
    return myEvaluationContext;
  }

  public boolean isResumed() {
    return myIsResumed;
  }

  public void setIsEvaluating(EvaluationContextImpl evaluationContext) {
    assertNotResumed();
    myEvaluationContext = evaluationContext;
  }

  public String toString() {
    if (myEventSet != null) {
      return myEventSet.toString();
    } 
    return myThread != null ? myThread.toString() : DebuggerBundle.message("string.null.context");
  }

  public void keep(ObjectReference reference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      final boolean added = myKeptReferences.add(reference);
      if (added) {
        try {
          reference.disableCollection();
        }
        catch (UnsupportedOperationException e) {
          // ignore: some J2ME implementations does not provide this operation
        }
      }
    }
  }

  public void postponeCommand(final SuspendContextCommandImpl command) {
    if (!isResumed()) {
      myPostponedCommands.add(command);
    }
    else {
      command.notifyCancelled();
    }
  }

  public SuspendContextCommandImpl pollPostponedCommand() {
    return myPostponedCommands.poll();
  }
}
