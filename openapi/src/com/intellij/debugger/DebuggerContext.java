package com.intellij.debugger;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface DebuggerContext extends StackFrameContext{
  SuspendContext getSuspendContext();

  Project getProject();
}
