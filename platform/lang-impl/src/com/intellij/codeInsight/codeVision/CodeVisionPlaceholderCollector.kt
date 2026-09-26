// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.codeVision

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface CodeVisionPlaceholderCollector

@Internal
interface BypassBasedPlaceholderCollector : CodeVisionPlaceholderCollector {
  fun collectPlaceholders(element: PsiElement, editor: Editor) : List<TextRange>
}

interface GenericPlaceholderCollector : CodeVisionPlaceholderCollector {
  fun collectPlaceholders(editor: Editor): List<TextRange>
}