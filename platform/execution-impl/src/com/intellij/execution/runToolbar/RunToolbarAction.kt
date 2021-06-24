// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

interface RunToolbarAction {
  enum class FlexibleType {
    ExecutorButton,
    Flexible,
    Stable,
    Fixed
  }

  fun getFlexibleType(): FlexibleType = FlexibleType.Flexible
}