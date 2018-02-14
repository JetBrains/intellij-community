// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.impl.DebuggerSession;

import java.util.List;

public interface HotSwapStatusListener {
  void hotSwapFinished(boolean aborted, int errors, int warnings, List<DebuggerSession> sessions);
}
