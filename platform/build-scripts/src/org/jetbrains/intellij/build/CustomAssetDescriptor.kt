// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.SupportedDistribution

@ApiStatus.Internal
interface CustomAssetDescriptor {
  /** Relative to plugin dir (not to plugin lib dir) */
  val relativePath: String?
    get() = null

  val platformSpecific: SupportedDistribution?

  fun getSources(context: BuildContext): Sequence<LazySource>?
}