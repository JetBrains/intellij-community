package com.intellij.debugger.engine;

import com.intellij.debugger.engine.SuspendContextImpl;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface SuspendContextRunnable  {
  void run(SuspendContextImpl suspendContext) throws Exception;
}
