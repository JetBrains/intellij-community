// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.window.to.foreground

import com.intellij.ui.mac.foundation.Foundation

internal class MacBringProcessWindowToForegroundSupport : BringProcessWindowToForegroundSupport {
  override fun bring(pid: UInt): Boolean {
    val nsRunningApplicationClass = Foundation.getObjcClass("NSRunningApplication")
    val macAcceptableIntPid = pid.toInt()
    val nsApplication = Foundation.invoke(nsRunningApplicationClass, "runningApplicationWithProcessIdentifier:", macAcceptableIntPid)
    return Foundation.invoke(nsApplication, "activateWithOptions:", 1).booleanValue()
  }
}