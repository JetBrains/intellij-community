// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

data class WslDistributionAndVersion(val distributionName: String, val version: Int) {
  override fun toString(): String {
    return "($distributionName, version=$version)"
  }
}
