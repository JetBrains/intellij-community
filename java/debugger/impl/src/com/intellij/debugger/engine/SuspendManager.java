/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.event.EventSet;
import org.jetbrains.annotations.NotNull;

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

  void resumeThread(SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl invokeThread);
  void suspendThread(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl invokeThread);

  void voteResume(SuspendContextImpl suspendContext);
  void voteSuspend(SuspendContextImpl suspendContext);

  List<SuspendContextImpl> getEventContexts();
}
