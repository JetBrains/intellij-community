package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import com.sun.jdi.ClassLoaderReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface EvaluationContext extends StackFrameContext{
  DebugProcess getDebugProcess();

  EvaluationContext createEvaluationContext(Value value);

  SuspendContext getSuspendContext();

  Project getProject();

  ClassLoaderReference getClassLoader() throws EvaluateException;
}
