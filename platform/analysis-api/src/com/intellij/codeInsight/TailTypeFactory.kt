// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Factory service for creating [TailType] instances.
 *
 * This abstraction allows returning different implementations (e.g., frontend-friendly)
 * depending on the runtime environment, while keeping [TailTypes] as the stable API.
 */
@ApiStatus.Internal
interface TailTypeFactory {
  fun noneType(): TailType
  fun semicolonType(): TailType
  fun spaceType(): TailType
  fun insertSpaceType(): TailType
  fun humbleSpaceBeforeWordType(): TailType
  fun dotType(): TailType
  fun caseColonType(): TailType
  fun equalsType(): TailType
  fun conditionalExpressionColonType(): TailType
  fun charType(char: Char): TailType
  fun charType(char: Char, overwrite: Boolean): TailType
  fun unknownType(): TailType

  companion object {
    @JvmStatic
    fun getInstance(): TailTypeFactory = service<TailTypeFactory>()
  }
}
