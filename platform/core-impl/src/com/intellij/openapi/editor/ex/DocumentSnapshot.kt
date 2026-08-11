// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Internal
interface DocumentSnapshot {

  @Contract(pure = true)
  fun text(): DocumentText

  @Contract(pure = true)
  fun withText(text: DocumentText): DocumentSnapshot

  @Contract(pure = true)
  fun dumpState(): String
}
