package com.intellij.debugger.engine.jdi;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.ThreadReference;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface ThreadReferenceProxy extends ObjectReferenceProxy{
  VirtualMachineProxy getVirtualMachine();
  ThreadReference     getThreadReference();

  StackFrameProxy frame(int i) throws EvaluateException;
}
