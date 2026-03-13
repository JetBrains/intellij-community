// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.TailType
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
  override fun noneType(): TailType = NoneTailType

  override fun semicolonType(): TailType = FrontendFriendlyCharTailType(';')

  override fun spaceType(): TailType = FrontendFriendlyCharTailType(' ')

  override fun insertSpaceType(): TailType = FrontendFriendlyCharTailType(' ', false)

  override fun unknownType(): TailType = FrontendFriendlyUnknownTailType

  override fun humbleSpaceBeforeWordType(): TailType = HumbleSpaceBeforeWordTailType

  override fun dotType(): TailType = FrontendFriendlyCharTailType('.')

  override fun caseColonType(): TailType = FrontendFriendlyCharTailType(':')

  override fun equalsType(): TailType = FrontendFriendlyCharTailType('=')

  override fun conditionalExpressionColonType(): TailType = CondExprColonTailType

  override fun charType(char: Char): TailType = FrontendFriendlyCharTailType(char)

  override fun charType(char: Char, overwrite: Boolean): TailType = FrontendFriendlyCharTailType(char, overwrite)
}
