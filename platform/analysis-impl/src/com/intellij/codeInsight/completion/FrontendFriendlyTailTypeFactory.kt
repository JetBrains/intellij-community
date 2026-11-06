// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.ModNavigatorTailType
import com.intellij.codeInsight.TailTypeFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Frontend-friendly implementation of [TailTypeFactory][com.intellij.codeInsight.TailTypeFactory]
 * that returns [FrontendFriendlyTailType] implementations.
 *
 * These implementations are safe to serialize and execute on the frontend
 * in Remote Development environments.
 */
@ApiStatus.Internal
class FrontendFriendlyTailTypeFactory : TailTypeFactory {
  override fun noneType(): ModNavigatorTailType = NoneTailType

  override fun semicolonType(): ModNavigatorTailType = FrontendFriendlyCharTailType(';')

  override fun spaceType(): ModNavigatorTailType = FrontendFriendlyCharTailType(' ')

  override fun insertSpaceType(): ModNavigatorTailType = FrontendFriendlyCharTailType(' ', false)

  override fun unknownType(): ModNavigatorTailType = FrontendFriendlyUnknownTailType

  override fun humbleSpaceBeforeWordType(): ModNavigatorTailType = HumbleSpaceBeforeWordTailType

  override fun dotType(): ModNavigatorTailType = FrontendFriendlyCharTailType('.')

  override fun caseColonType(): ModNavigatorTailType = FrontendFriendlyCharTailType(':')

  override fun equalsType(): ModNavigatorTailType = FrontendFriendlyCharTailType('=')

  override fun conditionalExpressionColonType(): ModNavigatorTailType = CondExprColonTailType

  override fun charType(char: Char): ModNavigatorTailType = FrontendFriendlyCharTailType(char)

  override fun charType(char: Char, overwrite: Boolean): ModNavigatorTailType = FrontendFriendlyCharTailType(char, overwrite)
}
