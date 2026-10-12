// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.DebuggerUtils
import com.sun.jdi.VirtualMachine

object DexDebugFacility {
  @Deprecated(message = "Use DebuggerUtils.isAndroidVM(virtualMachine) instead",
              replaceWith = ReplaceWith("DebuggerUtils.isAndroidVM(virtualMachine)"))
  fun isDex(virtualMachine: VirtualMachine): Boolean = DebuggerUtils.isAndroidVM(virtualMachine)
}