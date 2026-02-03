// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IncompleteIndexingToken {
  @Volatile
  private var successful = true

  fun markUnsuccessful() {
    successful = false
  }

  fun isSuccessful(): Boolean = successful
}