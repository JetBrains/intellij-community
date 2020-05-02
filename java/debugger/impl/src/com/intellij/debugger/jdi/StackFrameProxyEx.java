// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StackFrameProxyEx extends StackFrameProxy {
  @Nullable ObjectReference thisObject() throws EvaluateException;

  Value getVariableValue(@NotNull LocalVariableProxy localVariable) throws EvaluateException;
}
