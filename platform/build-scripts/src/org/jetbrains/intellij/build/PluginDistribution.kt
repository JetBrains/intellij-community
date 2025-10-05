// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

/**
 * * [ALL]
 * * [NOT_FOR_RELEASE]
 * * [NOT_FOR_PUBLIC_BUILDS]
 */
enum class PluginDistribution {
  /**
   * A plugin should be included all distribution for EAP, Nightly, Release and all other distributions
   */
  ALL,

  /**
   * A plugin should be included in distribution for EAP and Nightly builds only (non Release)
   */
  NOT_FOR_RELEASE,

  /**
   * A plugin should be included in distribution for nightly builds only (non EAP, non Release)
   */
  NOT_FOR_PUBLIC_BUILDS,
}
