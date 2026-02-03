// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface AppIndexingDependenciesToken {
  /**
   * Monotonically increasing number
   */
  fun toInt(): Int
  fun mergeWith(other: AppIndexingDependenciesToken): AppIndexingDependenciesToken
}