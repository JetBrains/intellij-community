// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

interface RTBarAction {
  enum class Type {
    RIGHT_FLEXIBLE,
    RIGHT_STABLE,
    STABLE,
    FLEXIBLE
  }

  fun getRightSideType(): Type = Type.STABLE
}

interface RTRunConfiguration : RTBarAction {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.STABLE
  fun isStable(): Boolean {
    return getRightSideType() == RTBarAction.Type.STABLE
  }
}
