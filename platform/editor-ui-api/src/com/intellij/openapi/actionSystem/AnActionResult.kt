// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import org.jetbrains.annotations.ApiStatus

/**
 * A result of an action update or perform calls.
 * It provides a sealed hierarchy to describe
 * whether the action was really [Performed], [Ignored], or [Failed].
 */
sealed class AnActionResult {
  val isPerformed: Boolean
    get() = this is Performed

  val isIgnored: Boolean
    get() = this is Ignored

  val isFailed: Boolean
    get() = this is Failed

  class Performed @ApiStatus.Internal constructor() : AnActionResult()

  class Ignored @ApiStatus.Internal constructor(
    val reason: String,
  ) : AnActionResult()

  class Failed @ApiStatus.Internal constructor(
    val cause: Throwable,
  ) : AnActionResult()

  @ApiStatus.Internal
  companion object {
    @JvmField
    @ApiStatus.Internal
    val IGNORED: AnActionResult = Ignored("unknown reason")

    @JvmField
    @ApiStatus.Internal
    val PERFORMED: AnActionResult = Performed()

    @JvmStatic
    @ApiStatus.Internal
    fun failed(cause: Throwable): AnActionResult {
      return Failed(cause)
    }

    @JvmStatic
    @ApiStatus.Internal
    fun ignored(reason: String): AnActionResult {
      return Ignored(reason)
    }
  }
}
