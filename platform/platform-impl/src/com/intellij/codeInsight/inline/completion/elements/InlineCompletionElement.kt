// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.render.InlineCompletionBlock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Deprecated(
  message = "Use [InlineCompletionGrayTextElement] instead",
  replaceWith = ReplaceWith("InlineCompletionGrayTextElement(text)"),
  level = DeprecationLevel.WARNING
)
class InlineCompletionElement(text: String) : InlineCompletionBlock by InlineCompletionGrayTextElement(text)
