// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface DocumentTextOp {
  interface Insert : DocumentTextOp {
    fun offset(): Int
    fun fragment(): CharSequence
  }

  interface Delete : DocumentTextOp {
    fun offset(): Int
    fun length(): Int
  }
}
