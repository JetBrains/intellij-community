package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.InternalException;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 24, 2004
 * Time: 8:04:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class SuspendManagerImpl implements SuspendManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendManager");

  private final LinkedList<SuspendContextImpl> myEventContexts  = new LinkedList<SuspendContextImpl>();
  // contexts, paused at breakpoint or another debugger event requests. Note that thread, explicitly paused by user is not considered as
  // "paused at breakpoint" and JDI prohibits data queries on its stackframes 
  private final LinkedList<SuspendContextImpl> myPausedContexts = new LinkedList<SuspendContextImpl>();
  private final Set<ThreadReferenceProxyImpl>  myFrozenThreads  = new HashSet<ThreadReferenceProxyImpl>();

  private final DebugProcessImpl myDebugProcess;

  public int suspends = 0;

  public SuspendManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      public void processDetached(DebugProcessImpl process, boolean closedByUser) {
        myEventContexts.clear();
        myPausedContexts.clear();
        myFrozenThreads.clear();
      }
    });
  }

  public SuspendContextImpl pushSuspendContext(final int suspendPolicy, int nVotes) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, suspendPolicy, nVotes, null) {
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
                if (/*Patches.MAC_RESUME_VM_HACK && */e.errorCode() == 13) {
                  //Its funny, but second resume solves the problem
                  continue;
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

  public SuspendContextImpl pushSuspendContext(final EventSet set) {
    SuspendContextImpl suspendContext = new SuspendContextImpl(myDebugProcess, set.suspendPolicy(), set.size(), set) {
      protected void resumeImpl() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Start resuming eventSet " + set.toString() + " suspendPolicy = " + set.suspendPolicy() + ",size = " + set.size());
        }
        myDebugProcess.logThreads();
        final ThreadReferenceProxyImpl thread = getThread();

        if (thread != null && !thread.isCollected()) {
          LOG.assertTrue(thread.isSuspended(), "Context thread must be suspended");
        }

        int attempts = 5;
        while (--attempts > 0) {
          try {
            set.resume();
            break;
          }
          catch (InternalException e) {
            //InternalException 13 means that there are running threads that we are trying to resume
            //On MacOS it happened that native thread didn't stop while some java thread reached breakpoint
            if (/*Patches.MAC_RESUME_VM_HACK && */e.errorCode() == 13 && set.suspendPolicy() == EventRequest.SUSPEND_ALL) {
              //Its funny, but second resume solves the problem
              continue;
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

  public void resume(SuspendContextImpl context) {
    SuspendManagerUtil.prepareForResume(context);

    myDebugProcess.logThreads();
    int suspendPolicy = context.getSuspendPolicy();
    context.resume();
    popContext(context);
    myDebugProcess.clearCashes(suspendPolicy);
  }

  public void popFrame(SuspendContextImpl suspendContext) {
    popContext(suspendContext);
    SuspendContextImpl newSuspendContext = pushSuspendContext(suspendContext.getSuspendPolicy(), 0);
    newSuspendContext.setThread(suspendContext.getThread().getThreadReference());
    notifyPaused(newSuspendContext);
  }

  public SuspendContextImpl getPausedContext() {
    return !myPausedContexts.isEmpty() ? myPausedContexts.getFirst() : null;
  }

  public void popContext(SuspendContextImpl suspendContext) {
    suspends--;
    if (LOG.isDebugEnabled()) {
      LOG.debug("popContext, suspends = " + suspends);
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final boolean removed = myEventContexts.remove(suspendContext);
    if (!removed) {
      LOG.assertTrue(false, suspendContext.toString());
    }
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

  public List<SuspendContextImpl> getEventContexts() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myEventContexts;
  }

  public boolean isFrozen(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myFrozenThreads.contains(thread);
  }

  public boolean isSuspended(ThreadReferenceProxyImpl thread) {
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

  public void suspendThread(SuspendContextImpl context, ThreadReferenceProxyImpl thread) {
    LOG.assertTrue(thread != context.getThread(), "Thread is already suspended at the breakpoint");

    if(context.isExplicitlyResumed(thread)) {
      context.myResumedThreads.remove(thread);
      thread.suspend();
    }
  }

  public void resumeThread(SuspendContextImpl context, ThreadReferenceProxyImpl thread) {
    LOG.assertTrue(thread != context.getThread(), "Use resume() instead of resuming breakpoint thread");
    LOG.assertTrue(!context.isExplicitlyResumed(thread));

    if(context.myResumedThreads == null) {
      context.myResumedThreads = new HashSet<ThreadReferenceProxyImpl>();
    }
    context.myResumedThreads.add(thread);
    thread.resume();
  }

  public void freezeThread(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(!isFrozen(thread));
    myFrozenThreads.add(thread);
    thread.suspend();
  }

  public void unfreezeThread(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(isFrozen(thread));
    myFrozenThreads.remove(thread);
    thread.resume();
  }

  private void processVote(SuspendContextImpl suspendContext) {
    LOG.assertTrue(suspendContext.myVotesToVote > 0);
    suspendContext.myVotesToVote--;

    if (LOG.isDebugEnabled()) {
      LOG.debug("myVotesToVote = " +  suspendContext.myVotesToVote);
    }
    if(suspendContext.myVotesToVote == 0) {
      if(suspendContext.myIsVotedForResume) {
        resume(suspendContext);
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug("vote paused");
        }
        myDebugProcess.logThreads();
        myDebugProcess.cancelRunToCursorBreakpoint();
        myDebugProcess.deleteStepRequests(suspendContext.getThread());
        notifyPaused(suspendContext);
      }
    }
  }

  public void notifyPaused(SuspendContextImpl suspendContext) {
    pushPausedContext(suspendContext);
    myDebugProcess.myDebugProcessDispatcher.getMulticaster().paused(suspendContext);
  }

  public void voteResume(SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resume voted");
    }
    processVote(suspendContext);
  }

  public void voteSuspend(SuspendContextImpl suspendContext) {
    suspendContext.myIsVotedForResume = false;
    processVote(suspendContext);
  }

  LinkedList<SuspendContextImpl> getPausedContexts() {
    return myPausedContexts;
  }
}
