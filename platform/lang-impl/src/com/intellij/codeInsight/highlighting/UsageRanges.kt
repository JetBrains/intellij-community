// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class UsageRanges(
  val readRanges: Collection<TextRange>,
  val writeRanges: Collection<TextRange>,
  val readDeclarationRanges: Collection<TextRange>,
  val writeDeclarationRanges: Collection<TextRange>
)
