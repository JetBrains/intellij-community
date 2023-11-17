// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

internal class TypedEvent(private val charTyped: Char, private val offset: Int) {
  override fun toString(): String = "charTyped: $charTyped; offset: $offset"
}