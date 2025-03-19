// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface MutableActionChangeRange : ActionChangeRange {
  var state: ImmutableActionChangeRange

  val originalTimestamp: Int
  val isMoved get() = originalTimestamp != state.timestamp

  override fun asInverted(): MutableActionChangeRange
}