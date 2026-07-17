// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.Failure
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ExecutionConsole

internal interface BuildConsoleView : ExecutionConsole {

  val consoleView: ConsoleView

  fun onEvent(event: BuildEvent)

  fun onFailure(failure: Failure)
}