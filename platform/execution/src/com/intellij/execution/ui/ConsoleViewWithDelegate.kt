// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.execution.ui

import org.jetbrains.annotations.ApiStatus

interface ConsoleViewWithDelegate {
  val delegate: ConsoleView
}

@ApiStatus.Experimental
fun ExecutionConsole.unwrapDelegate(): ExecutionConsole {
  return (this as? ConsoleViewWithDelegate)?.delegate?.unwrapDelegate() ?: this
}