// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.ml

import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.platform.ml.Tier
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TierTyping : Tier<TypingSpeedTracker>()

@ApiStatus.Internal
object TierCaretLocation : Tier<CaretLocation>()

@ApiStatus.Internal
data class CaretLocation(
  val psiFile: PsiFile,
  val editor: Editor,
  val offset: Int,
  val language: Language
)
