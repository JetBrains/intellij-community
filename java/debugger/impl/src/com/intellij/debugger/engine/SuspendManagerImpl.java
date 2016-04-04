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
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.InternalException;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author lex
 */
public class SuspendManagerImpl implements SuspendManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendManager");

  private final LinkedList<SuspendContextImpl> myEventContexts  = new LinkedList<>();
  /**
   * contexts, paused at breakpoint or another debugger event requests. Note that thread, explicitly paused by user is not considered as
   * "paused at breakpoint" and JDI prohibits data queries on its stack frames
   */
  private final LinkedList<SuspendContextImpl> myPausedContexts = new LinkedList<>();
  private final Set<ThreadReferenceProxyImpl>  myFrozenThreads  = Collections.synchronizedSet(new HashSet<ThreadReferenceProxyImpl>());

  private final DebugProcessImpl myDebugProcess;

  public int suspends = 0;

  public SuspendManagerImpl(@NotNull DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      @Override
      public void processDetached(DebugProcessImpl process, boolean closedByUser) {
        myEventContexts.clear();
        myPausedContexts.clear();
        myFrozenThreads.clear();
      }
    });
  }

  @Override
  public SuspendContextImpl pushSuspendContext(@MagicConstant(flagsFromClass = EventRequest.class) final int suspendPolicy, int nVotes) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, suspendPolicy, nVotes, null) {
      @Override
      protected void resumeImpl() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Start resuming...");
        }
        myDebugProcess.logThreads();
        switch(getSuspendPolicy()) {
          case EventRequest.SUSPEND_ALL:
            int resumeAttempts = 5;
            while (--resumeAttempts > 0) {
              try {
                myDebugProcess.getVirtualMachineProxy().resume();
                break;
              }
              catch (InternalException e) {
                //InternalException 13 means that there are running threads that we are trying to resume
                //On MacOS it happened that native thread didn't stop while some java thread reached breakpoint
                //noinspection StatementWithEmptyBody
                if (/*Patches.MAC_RESUME_VM_HACK && */e.errorCode() == 13) {
                  //Its funny, but second resume solves the problem
                }
                else {
                  LOG.error(e);
                  break;
                }
              }
            }
            
            if (LOG.isDebugEnabled()) {
              LOG.debug("VM resumed ");
            }
            break;
          case EventRequest.SUSPEND_EVENT_THREAD:
            myFrozenThreads.remove(getThread());
            getThread().resume();
            if(LOG.isDebugEnabled()) {
              LOG.debug("Thread resumed : " + getThread().toString());
            }
            break;
          case EventRequest.SUSPEND_NONE:
            if (LOG.isDebugEnabled()) {
              LOG.debug("None resumed");
            }
            break;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Suspends = " + suspends);
        }
        myDebugProcess.logThreads();
      }
    };
    pushContext(suspendContext);
    return suspendContext;
  }

  @Override
  public SuspendContextImpl pushSuspendContext(final EventSet set) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, set.suspendPolicy(), set.size(), set) {
      @Override
      protected void resumeImpl() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Start resuming eventSet " + set.toString() + " suspendPolicy = " + set.suspendPolicy() + ",size = " + set.size());
        }
        myDebugProcess.logThreads();
        //final ThreadReferenceProxyImpl thread = getThread();
        //
        //if (thread != null) { // check that thread is suspended at the moment
        //  try {
        //    if (!thread.isSuspended()) {
        //      final int status = thread.status();
        //      if ((status != ThreadReference.THREAD_STATUS_ZOMBIE) && (status != ThreadReference.THREAD_STATUS_NOT_STARTED) && (status != ThreadReference.THREAD_STATUS_UNKNOWN)) {
        //        LOG.error("Context thread must be suspended");
        //      }
        //    }
        //  }
        //  catch (ObjectCollectedException ignored) {}
        //}

        int attempts = 5;
        while (--attempts > 0) {
          try {
            set.resume();
            break;
          }
          catch (ObjectCollectedException e) {
            // according to error reports set.resume() may throw this if one of the threads has been collected
            LOG.info(e);
          }
          catch (InternalException e) {
            //InternalException 13 means that there are running threads that we are trying to resume
            //On MacOS it happened that native thread didn't stop while some java thread reached breakpoint
            //noinspection StatementWithEmptyBody
            if (/*Patches.MAC_RESUME_VM_HACK && */e.errorCode() == 13 && set.suspendPolicy() == EventRequest.SUSPEND_ALL) {
              //Its funny, but second resume solves the problem
            }
            else {
              LOG.error(e);
              break;
            }
          }
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Set resumed ");
        }
        myDebugProcess.logThreads();
      }
    };
    pushContext(suspendContext);
    return suspendContext;
  }

  private void pushContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myEventContexts.addFirst(suspendContext);
    suspends++;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Push context : Suspends = " + suspends);
    }
  }

  @Override
  public void resume(SuspendContextImpl context) {
    SuspendManagerUtil.prepareForResume(context);

    myDebugProcess.logThreads();
    popContext(context);
    context.resume();
    myDebugProcess.clearCashes(context.getSuspendPolicy());
  }

  @Override
  public void popFrame(SuspendContextImpl suspendContext) {
    popContext(suspendContext);
    SuspendContextImpl newSuspendContext = pushSuspendContext(suspendContext.getSuspendPolicy(), 0);
    newSuspendContext.setThread(suspendContext.getThread().getThreadReference());
    notifyPaused(newSuspendContext);
  }

  @Override
  public SuspendContextImpl getPausedContext() {
    return !myPausedContexts.isEmpty() ? myPausedContexts.getFirst() : null;
  }

  public void popContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    suspends--;
    if (LOG.isDebugEnabled()) {
      LOG.debug("popContext, suspends = " + suspends);
    }
    myEventContexts.remove(suspendContext);
    myPausedContexts.remove(suspendContext);
  }

  void pushPausedContext(SuspendContextImpl suspendContext) {
    if(LOG.isDebugEnabled()) {
      LOG.assertTrue(myEventContexts.contains(suspendContext));
    }

    myPausedContexts.addFirst(suspendContext);
  }

  public boolean hasEventContext(SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myEventContexts.contains(suspendContext);
  }

  @Override
  public List<SuspendContextImpl> getEventContexts() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return Collections.unmodifiableList(myEventContexts);
  }

  @Override
  public boolean isFrozen(ThreadReferenceProxyImpl thread) {
    return myFrozenThreads.contains(thread);
  }

  @Override
  public boolean isSuspended(ThreadReferenceProxyImpl thread) throws ObjectCollectedException{
    DebuggerManagerThreadImpl.assertIsManagerThread();

    boolean suspended = false;

    if (isFrozen(thread)) {
      suspended = true;
    }
    else {
      for (SuspendContextImpl suspendContext : myEventContexts) {
        if (suspendContext.suspends(thread)) {
          suspended = true;
          break;
        }
      }
    }

    //bug in JDI : newly created thread may be resumed even when suspendPolicy == SUSPEND_ALL
    //if(LOG.isDebugEnabled() && suspended) {
    //  LOG.assertTrue(thread.suspends(), thread.name());
    //}
    return suspended && (thread == null || thread.isSuspended());
  }

  @Override
  public void suspendThread(SuspendContextImpl context, ThreadReferenceProxyImpl thread) {
    LOG.assertTrue(thread != context.getThread(), "Thread is already suspended at the breakpoint");

    if(context.isExplicitlyResumed(thread)) {
      context.myResumedThreads.remove(thread);
      thread.suspend();
    }
  }

  @Override
  public void resumeThread(SuspendContextImpl context, @NotNull ThreadReferenceProxyImpl thread) {
    //LOG.assertTrue(thread != context.getThread(), "Use resume() instead of resuming breakpoint thread");
    LOG.assertTrue(!context.isExplicitlyResumed(thread));

    if(context.myResumedThreads == null) {
      context.myResumedThreads = new HashSet<>();
    }
    context.myResumedThreads.add(thread);
    thread.resume();
  }

  @Override
  public void freezeThread(ThreadReferenceProxyImpl thread) {
    if (myFrozenThreads.add(thread)) {
      thread.suspend();
    }
  }

  @Override
  public void unfreezeThread(ThreadReferenceProxyImpl thread) {
    if (myFrozenThreads.remove(thread)) {
      thread.resume();
    }
  }

  private void processVote(final SuspendContextImpl suspendContext) {
    LOG.assertTrue(suspendContext.myVotesToVote > 0);
    suspendContext.myVotesToVote--;

    if (LOG.isDebugEnabled()) {
      LOG.debug("myVotesToVote = " +  suspendContext.myVotesToVote);
    }
    if(suspendContext.myVotesToVote == 0) {
      if(suspendContext.myIsVotedForResume) {
        // resume in a separate request to allow other requests be processed (e.g. dependent bpts enable)
        myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            resume(suspendContext);
          }

          @Override
          public Priority getPriority() {
            return Priority.HIGH;
          }
        });
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("vote paused");
        }
        myDebugProcess.logThreads();
        myDebugProcess.cancelRunToCursorBreakpoint();
        final ThreadReferenceProxyImpl thread = suspendContext.getThread();
        myDebugProcess.deleteStepRequests(thread != null? thread.getThreadReference() : null);
        notifyPaused(suspendContext);
      }
    }
  }

  public void notifyPaused(SuspendContextImpl suspendContext) {
    pushPausedContext(suspendContext);
    myDebugProcess.myDebugProcessDispatcher.getMulticaster().paused(suspendContext);
  }

  @Override
  public void voteResume(SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resume voted");
    }
    processVote(suspendContext);
  }

  @Override
  public void voteSuspend(SuspendContextImpl suspendContext) {
    suspendContext.myIsVotedForResume = false;
    processVote(suspendContext);
  }

  public List<SuspendContextImpl> getPausedContexts() {
    return myPausedContexts;
  }
}
