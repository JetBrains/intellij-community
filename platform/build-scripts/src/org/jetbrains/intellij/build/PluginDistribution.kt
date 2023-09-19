// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

sealed interface PluginDistribution {
  fun accept(context: BuildContext): Boolean

  /**
   * Use [PluginDistribution.Nightly] if the plugin should be included in distribution for nightly builds only (non EAP, non Release).
   */
  object Nightly : PluginDistribution {
    override fun accept(context: BuildContext) = context.options.isNightlyBuild
  }

  /**
   * Use [PluginDistribution.EAP] if the plugin should be included in distribution for EAP and Nightly builds only (non Release).
   */
  object EAP : PluginDistribution {
    override fun accept(context: BuildContext) = Nightly.accept(context) || context.applicationInfo.isEAP
  }

  /**
   * Use [PluginDistribution.Release] if the plugin should be included all distribution for EAP, Nightly and Release.
   */
  object Release : PluginDistribution {
    override fun accept(context: BuildContext) = EAP.accept(context) || context.applicationInfo.isRelease
  }
}
