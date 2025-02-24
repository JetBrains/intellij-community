// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalSession {
  suspend fun sendInputEvent(event: TerminalInputEvent)

  suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>>
}