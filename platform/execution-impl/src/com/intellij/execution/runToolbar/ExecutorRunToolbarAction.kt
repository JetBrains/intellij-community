// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

internal interface ExecutorRunToolbarAction : RTBarAction {
  val process: RunToolbarProcess

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE
}