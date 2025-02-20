// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalOutputBlock(
  /** Unique zero-based ID of the block */
  val id: Int,
  val startOffset: Int,
  val commandStartOffset: Int,
  val outputStartOffset: Int,
  val endOffset: Int,
  /**
   * Exit code of the command executed in this block.
   * If code is null, then no command was executed.
   */
  val exitCode: Int?,
)