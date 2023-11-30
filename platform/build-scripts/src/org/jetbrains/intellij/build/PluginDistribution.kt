// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

enum class PluginDistribution {
  /**
   * Use [PluginDistribution.ALL] if the plugin should be included all distribution for EAP, Nightly, Release and all other distributions.
   */
  ALL,
  /**
   * Use [PluginDistribution.NOT_FOR_RELEASE] if the plugin should be included in distribution for EAP and Nightly builds only (non Release).
   */
  NOT_FOR_RELEASE,
  /**
   * Use [PluginDistribution.NOT_FOR_PUBLIC_BUILDS] if the plugin should be included in distribution for nightly builds only (non EAP, non Release).
   */
  NOT_FOR_PUBLIC_BUILDS,
}
