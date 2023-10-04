// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

@Suppress("FunctionName")
@ApiStatus.Experimental
fun InlineCompletionElement(text: String) = InlineCompletionGrayTextElement(text)
