/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;

public interface SuspendContext extends StackFrameContext {
  public int getSuspendPolicy();

  ThreadReferenceProxy getThread();
}
