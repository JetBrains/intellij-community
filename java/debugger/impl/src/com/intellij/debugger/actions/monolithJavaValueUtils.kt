// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.engine.JavaValue
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.util.MonolithUtils
import kotlinx.coroutines.runBlocking

internal fun findJavaValue(xValue: XValue, sessionProxy: XDebugSessionProxy): JavaValue? {
  if (xValue is JavaValue) return xValue
  if (!XDebugSessionProxy.useFeProxy()) return null // should be a JavaValue otherwise
  if (!MonolithUtils.isMonolith()) return null
  @Suppress("RAW_RUN_BLOCKING") // no actual suspend inside runBlocking
  return runBlocking {
    XDebugManagerProxy.getInstance().withId(xValue, sessionProxy) { xValueId ->
      MonolithUtils.findXValueById(xValueId) as? JavaValue
    }
  }
}