package com.intellij.debugger.impl;

import com.intellij.debugger.impl.DebuggerSession;

import java.util.EventListener;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DebuggerManagerListener extends EventListener{
  void sessionCreated(DebuggerSession session);
  void sessionRemoved(DebuggerSession session);
}
