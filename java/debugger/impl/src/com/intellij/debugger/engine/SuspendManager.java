// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SuspendManager {
  @NotNull
  SuspendContextImpl pushSuspendContext(@NotNull EventSet eventSet);

  @NotNull
  SuspendContextImpl pushSuspendContext(int suspendAll, int i);

  void resume(@NotNull SuspendContextImpl suspendContext);

  /**
   * Replaces current context with new one at the same location and fires 'paused' event.
   */
  void popFrame(@NotNull SuspendContextImpl suspendContext);

  /**
   * Get first of paused contexts or {@code null} if there are no ones.
   */
  SuspendContextImpl getPausedContext();

  /**
   * Get all paused contexts.
   */
  @NotNull
  List<SuspendContextImpl> getPausedContexts();

  boolean isFrozen(@NotNull ThreadReferenceProxyImpl thread);

  boolean isSuspended(@NotNull ThreadReferenceProxyImpl thread) throws ObjectCollectedException;

  void freezeThread(@NotNull ThreadReferenceProxyImpl invokeThread);

  void unfreezeThread(@NotNull ThreadReferenceProxyImpl thread);

  void resumeThread(@NotNull SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl invokeThread);

  void suspendThread(@NotNull SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl invokeThread);

  void voteResume(@NotNull SuspendContextImpl suspendContext);

  void voteSuspend(@NotNull SuspendContextImpl suspendContext);

  List<SuspendContextImpl> getEventContexts();
}
