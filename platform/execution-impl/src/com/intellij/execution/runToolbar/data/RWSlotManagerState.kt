// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

enum class RWSlotManagerState {
  MULTIPLE,
  MULTIPLE_WITH_MAIN,
  SINGLE_MAIN,
  SINGLE_PLAIN,
  INACTIVE;

  fun isSinglePlain(): Boolean {
    return this == SINGLE_PLAIN
  }

  fun isActive(): Boolean {
    return this != INACTIVE
  }
}