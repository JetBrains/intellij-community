// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SuspendManager {
  @NotNull
  SuspendContextImpl pushSuspendContext(EventSet eventSet);

  @NotNull
  SuspendContextImpl pushSuspendContext(int suspendAll, int i);

  void resume(SuspendContextImpl suspendContext);

  /**
   * Replaces current context with new one at the same location and fires 'paused' event.
   */
  void popFrame(SuspendContextImpl suspendContext);

  /**
   * Get first of paused contexts or {@code null} if there are no ones.
   */
  SuspendContextImpl getPausedContext();

  /**
   * Get all paused contexts.
   */
  List<SuspendContextImpl> getPausedContexts();

  boolean isFrozen(ThreadReferenceProxyImpl thread);

  boolean isSuspended(ThreadReferenceProxyImpl thread) throws ObjectCollectedException;

  void freezeThread(ThreadReferenceProxyImpl invokeThread);

  void unfreezeThread(ThreadReferenceProxyImpl thread);

  void resumeThread(SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl invokeThread);

  void suspendThread(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl invokeThread);

  void voteResume(SuspendContextImpl suspendContext);

  void voteSuspend(SuspendContextImpl suspendContext);

  List<SuspendContextImpl> getEventContexts();
}
