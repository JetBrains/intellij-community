// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.feedback

import com.intellij.ide.util.PropertiesComponent
import com.intellij.xdebugger.frame.XStackFrame

private const val KOTLIN_WAS_DEBUGGED = "Kotlin-Was-Debugged"

class UsageTracker {
  companion object {
    @JvmStatic
    fun topFrameInitialized(topFrame: XStackFrame?) {
      if (topFrame?.sourcePosition?.file?.fileType?.name == "Kotlin") {
        PropertiesComponent.getInstance().setValue(KOTLIN_WAS_DEBUGGED, (kotlinDebuggedTimes() + 1).toString())
      }
    }

    fun kotlinDebuggedTimes(): Int = PropertiesComponent.getInstance().getInt(KOTLIN_WAS_DEBUGGED, 0)
  }
}