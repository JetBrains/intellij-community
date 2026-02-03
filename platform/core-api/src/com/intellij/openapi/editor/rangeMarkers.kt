// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.util.TextRange

val RangeMarker.asTextRange: TextRange?
  get() {
    if (!isValid) return null
    val start = startOffset
    val end = endOffset
    return if (start in 0..end) {
      TextRange(start, end)
    }
    else {
      // Probably a race condition had happened and range marker is invalidated
      null
    }
  }