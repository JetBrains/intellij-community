// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

sealed interface PluginDistribution {
  /**
   * Use [PluginDistribution.Nightly] if the plugin should be included in distribution for nightly builds only (non EAP, non Release).
   */
  object Nightly : PluginDistribution

  /**
   * Use [PluginDistribution.EAP] if the plugin should be included in distribution for EAP and Nightly builds only (non Release).
   */
  object EAP : PluginDistribution by Nightly

  /**
   * Use [PluginDistribution.Release] if the plugin should be included all distribution for EAP, Nightly and Release.
   */
  object Release: PluginDistribution by EAP
}
