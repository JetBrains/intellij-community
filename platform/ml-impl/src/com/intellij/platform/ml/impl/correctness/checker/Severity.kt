// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class Severity {
  CRITICAL,
  ACCEPTABLE,
}