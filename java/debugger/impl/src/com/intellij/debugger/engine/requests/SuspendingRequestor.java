// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.requests.Requestor;

public interface SuspendingRequestor extends Requestor {
  /**
   * @return either DebuggerSettings.SUSPEND_NONE or DebuggerSettings.SUSPEND_ALL or DebuggerSettings.SUSPEND_THREAD
   */
  String getSuspendPolicy();
}
