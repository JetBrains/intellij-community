// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.engine.JavaValue
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.frame.XValue
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import kotlinx.coroutines.runBlocking

internal fun findJavaValue(xValue: XValue, sessionProxy: XDebugSessionProxy): JavaValue? {
  if (xValue is JavaValue) return xValue
  if (!SplitDebuggerMode.isSplitDebugger()) return null // should be a JavaValue otherwise
  if (FrontendApplicationInfo.getFrontendType() is FrontendType.Remote) return null
  val managerProxy = XDebugManagerProxy.getInstance()
  if (!managerProxy.hasBackendCounterpart(xValue)) return null
  @Suppress("RAW_RUN_BLOCKING") // no actual suspend inside runBlocking
  return runBlocking {
    managerProxy.withId(xValue, sessionProxy) { xValueId ->
      XDebuggerEntityConverter.getValue(xValueId) as? JavaValue
    }
  }
}