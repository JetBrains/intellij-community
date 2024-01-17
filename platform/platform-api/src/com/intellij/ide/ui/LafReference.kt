// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
data class LafReference(@NlsSafe @JvmField val name: String, @JvmField val themeId: String) {
  companion object {
    val SEPARATOR: LafReference = LafReference("", "")
  }
}