// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalBlocksModelState(
  val blocks: List<TerminalOutputBlock>,
  val blockIdCounter: Int,
)