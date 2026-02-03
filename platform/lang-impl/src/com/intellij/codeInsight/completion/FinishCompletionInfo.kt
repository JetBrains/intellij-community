// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import org.jetbrains.annotations.ApiStatus

// hack for remote-development to pass item pattern and prefix length to the backend
@ApiStatus.Internal
class FinishCompletionInfo(
  val itemPattern: String,
  val prefixLength: Int,
)
