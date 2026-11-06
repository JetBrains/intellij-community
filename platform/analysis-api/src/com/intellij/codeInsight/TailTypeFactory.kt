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
  fun noneType(): ModNavigatorTailType
  fun semicolonType(): ModNavigatorTailType
  fun spaceType(): ModNavigatorTailType
  fun insertSpaceType(): ModNavigatorTailType
  fun humbleSpaceBeforeWordType(): ModNavigatorTailType
  fun dotType(): ModNavigatorTailType
  fun caseColonType(): ModNavigatorTailType
  fun equalsType(): ModNavigatorTailType
  fun conditionalExpressionColonType(): ModNavigatorTailType
  fun charType(char: Char): ModNavigatorTailType
  fun charType(char: Char, overwrite: Boolean): ModNavigatorTailType
  fun unknownType(): ModNavigatorTailType

  companion object {
    @JvmStatic
    fun getInstance(): TailTypeFactory = service<TailTypeFactory>()
  }
}
