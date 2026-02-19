// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.SupportedDistribution

internal interface CustomAssetDescriptor {
  /** Relative to plugin dir (not to plugin lib dir) */
  val relativePath: String?
    get() = null

  val platformSpecific: SupportedDistribution?

  suspend fun getSources(context: BuildContext): Sequence<LazySource>?
}