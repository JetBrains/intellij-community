// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.CharTailType
import org.jetbrains.annotations.ApiStatus

/**
 * Converter for [CharTailType] to [FrontendFriendlyCharTailType].
 */
@ApiStatus.Internal
class CharTailTypeConverter : TailTypeToFrontendFriendlyConverter<CharTailType> {
  override fun toDescriptor(target: CharTailType): FrontendFriendlyTailType? {
    // CharTailType stores the char and overwrite flag in private fields
    // We need to extract them - using reflection or toString() parsing
    val str = target.toString() // "CharTailType:'c'"
    if (!str.startsWith("CharTailType:'") || !str.endsWith("'")) {
      return null
    }
    val char = str[str.length - 2]
    // Default CharTailType uses overwrite=true
    return FrontendFriendlyCharTailType(char, true)
  }
}
