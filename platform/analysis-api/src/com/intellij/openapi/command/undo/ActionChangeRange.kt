// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("removal", "DEPRECATION", "unused")

package com.intellij.openapi.command.undo

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.ScheduledForRemoval
@Deprecated("CWM per-user undo stacks are being removed under IJPL-248573.")
interface ActionChangeRange {
  val offset: Int

  val oldLength: Int
  val newLength: Int

  val oldDocumentLength: Int
  val newDocumentLength: Int

  /**
   * Represents a unique identificator for
   */
  val id: Int

  /**
   * Represents a timestamp used to check if this range has been moved relative to another range with the same [id]
   */
  val timestamp: Int

  /**
   * Returns another range which shows the same range, but with its old and new lengths swapped
   */
  fun asInverted(): ActionChangeRange

  fun toImmutable(invalidate: Boolean): ImmutableActionChangeRange
}