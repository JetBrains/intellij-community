// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.util.Range;
import com.sun.jdi.Location;
import org.jetbrains.annotations.Nullable;

public interface MethodFilter {
  boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException;

  default boolean locationMatches(DebugProcessImpl process, Location location, @Nullable StackFrameProxyImpl frameProxy) throws EvaluateException {
    return locationMatches(process, location);
  }

  @Nullable Range<Integer> getCallingExpressionLines();

  default int onReached(SuspendContextImpl context, RequestHint hint) {
    return RequestHint.STOP;
  }

  default int getSkipCount() {
    return 0;
  }
}
