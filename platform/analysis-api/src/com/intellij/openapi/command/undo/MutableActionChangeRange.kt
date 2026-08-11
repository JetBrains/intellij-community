// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION", "unused")

package com.intellij.openapi.command.undo

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.ScheduledForRemoval
@Deprecated("CWM per-user undo stacks are being removed under IJPL-248573.")
interface MutableActionChangeRange : ActionChangeRange {
  var state: ImmutableActionChangeRange

  val originalTimestamp: Int
  val isMoved: Boolean get() = originalTimestamp != state.timestamp

  override fun asInverted(): MutableActionChangeRange
}