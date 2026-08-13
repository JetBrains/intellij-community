// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.Failure
import com.intellij.execution.ui.ExecutionConsole
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@Internal
interface BuildConsoleView : ExecutionConsole {

  fun onEvent(event: BuildEvent)

  fun onFailure(nodeId: Any, failure: Failure)

  fun scrollToNodeOutput(nodeId: Any)

  fun selectProgressOutput(nodeId: Any)

  @TestOnly
  fun getNodeOutputText(nodeId: Any): String
}