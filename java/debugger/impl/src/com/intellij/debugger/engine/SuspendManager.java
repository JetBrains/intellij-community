package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;

import java.util.List;

public interface SuspendManager {
  SuspendContextImpl pushSuspendContext(EventSet eventSet);
  SuspendContextImpl pushSuspendContext(int suspendAll, int i);

  void resume(SuspendContextImpl suspendContext);

  //replaces current context with new one at the same location and fires 'paused' event
  void popFrame(SuspendContextImpl suspendContext);

  SuspendContextImpl getPausedContext();
  boolean isFrozen(ThreadReferenceProxyImpl thread);
  boolean isSuspended(ThreadReferenceProxyImpl thread) throws ObjectCollectedException;

  void freezeThread(ThreadReferenceProxyImpl invokeThread);
  void unfreezeThread(ThreadReferenceProxyImpl thread);

  void resumeThread(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl invokeThread);
  void suspendThread(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl invokeThread);

  void voteResume(SuspendContextImpl suspendContext);
  void voteSuspend(SuspendContextImpl suspendContext);

  List<SuspendContextImpl> getEventContexts();
}
