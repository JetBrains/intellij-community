// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExecutorRunToolbarAction : RTBarAction {
  val process: RunToolbarProcess

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE
}