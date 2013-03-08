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
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public class DebuggerContextUtil {
  public static void setStackFrame(DebuggerStateManager manager, final StackFrameProxyImpl stackFrame) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerContextImpl context = manager.getContext();
    if(context == null) {
      return;
    }

    final DebuggerSession session = context.getDebuggerSession();
    final DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(session, context.getSuspendContext(), stackFrame.threadProxy(), stackFrame);

    manager.setState(newContext, session != null? session.getState() : DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_REFRESH, null);
  }

  public static void setThread(DebuggerStateManager contextManager, ThreadDescriptorImpl item) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebuggerContextImpl context = contextManager.getContext();
    if(context == null) {
      return;
    }

    final DebuggerSession session = context.getDebuggerSession();
    final DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(session, item.getSuspendContext(), item.getThreadReference(), null);

    contextManager.setState(newContext, session != null? session.getState() : DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_CONTEXT, null);
  }

  public static DebuggerContextImpl createDebuggerContext(@NotNull DebuggerSession session, SuspendContextImpl suspendContext){
    return DebuggerContextImpl.createDebuggerContext(session, suspendContext, suspendContext != null ? suspendContext.getThread() : null, null);
  }
}
