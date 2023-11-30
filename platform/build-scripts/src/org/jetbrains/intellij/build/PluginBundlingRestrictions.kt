// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.Objects

/**
 * Allows excluding a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
class PluginBundlingRestrictions private constructor(
  /**
   * Change this value if the plugin works on some OS only and therefore don't need to be bundled with distributions for other OS.
   */
  @JvmField
  val supportedOs: PersistentList<OsFamily>,

  /**
   * Change this value if the plugin works on some architectures only and
   * therefore don't need to be bundled with distributions for other architectures.
   */
  @JvmField
  val supportedArch: List<JvmArchitecture>,

  /**
   * Set to required [PluginDistribution] value depending on the distribution zone
   *  - Use [PluginDistribution.NOT_FOR_PUBLIC_BUILDS] if the plugin should be included in distribution for nightly builds only (non EAP, non Release).
   *  - Use [PluginDistribution.NOT_FOR_RELEASE] if the plugin should be included in distribution for EAP and Nightly builds only (non Release).
   *  - Use [PluginDistribution.ALL] if the plugin should be included all distribution for EAP, Nightly and Release.
   */
  @JvmField
  var includeInDistribution: PluginDistribution = PluginDistribution.ALL
) {
  companion object {
    @JvmField
    val NONE = PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, PluginDistribution.ALL)

    /**
     * Use for the plugin version which is uploaded to the Marketplace, since the latter does not support per-OS/ARCH plugins.
     * Bundled plugins must also have other PluginLayouts with different restrictions.
     *
     * If bundled and marketplace-uploaded versions of the plugin are identical, use [NONE] instead.
     */
    @JvmField
    val MARKETPLACE = PluginBundlingRestrictions(persistentListOf(), persistentListOf(), PluginDistribution.ALL)
  }

  class Builder {
    /**
     * Change this value if the plugin works on some OS only and therefore don't need to be bundled with distributions for other OS.
     */
    var supportedOs: PersistentList<OsFamily> = OsFamily.ALL

    /**
     * Change this value if the plugin works on some architectures only and
     * therefore don't need to be bundled with distributions for other architectures.
     */
    var supportedArch: List<JvmArchitecture> = JvmArchitecture.ALL

    @Deprecated("Use an explicit distribution", ReplaceWith("includeInDistribution == PluginDistribution.NOT_FOR_RELEASE"))
    var includeInEapOnly: Boolean
      get() = includeInDistribution == PluginDistribution.NOT_FOR_RELEASE
      set(_) {
        includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
      }

    @Deprecated("Use an explicit distribution", ReplaceWith("includeInDistribution == PluginDistribution.NOT_FOR_PUBLIC_BUILDS"))
    var includeInNightlyOnly: Boolean
      get() = includeInDistribution == PluginDistribution.NOT_FOR_PUBLIC_BUILDS
      set(_) {
        includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
      }

    /**
     * Set to required [PluginDistribution] value depending on the distribution zone
     *  - Use [PluginDistribution.NOT_FOR_PUBLIC_BUILDS] if the plugin should be included in distribution for nightly builds only (non EAP, non Release).
     *  - Use [PluginDistribution.NOT_FOR_RELEASE] if the plugin should be included in distribution for EAP and Nightly builds only (non Release).
     *  - Use [PluginDistribution.ALL] if the plugin should be included all distribution for EAP, Nightly and Release.
     */
    var includeInDistribution: PluginDistribution = PluginDistribution.ALL

    var marketplace: Boolean = false

    internal fun build(): PluginBundlingRestrictions {
      if (marketplace) {
        check(supportedOs == OsFamily.ALL)
        check(supportedArch == JvmArchitecture.ALL)
        check(includeInDistribution == PluginDistribution.ALL)
        @Suppress("DEPRECATION")
        check(!includeInEapOnly)
        @Suppress("DEPRECATION")
        check(!includeInNightlyOnly)
        return MARKETPLACE
      }
      return when (val restrictions = PluginBundlingRestrictions(supportedOs, supportedArch, includeInDistribution)) {
        NONE -> NONE
        else -> restrictions
      }
    }
  }

  init {
    if (supportedOs == OsFamily.ALL && supportedArch != JvmArchitecture.ALL) {
      error("os-independent, but arch-dependent plugins are currently not supported by build scripts")
    }
  }

  override fun toString(): String =
    if (this === MARKETPLACE) "marketplace"
    else if (this == NONE) "unrestricted"
    else "os: ${if (supportedOs == OsFamily.ALL) "unrestricted" else supportedOs.joinToString(",")}, " +
         "arch: ${if (supportedArch == JvmArchitecture.ALL) "unrestricted" else supportedArch.joinToString(",")}, " +
         "includeInDistribution=$includeInDistribution)"

  override fun hashCode(): Int =
    if (this === MARKETPLACE) -1
    else Objects.hash(supportedOs, supportedArch, includeInDistribution)

  @Suppress("RedundantIf")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this === MARKETPLACE) return false
    if (javaClass != other?.javaClass) return false
    other as PluginBundlingRestrictions
    if (supportedOs != other.supportedOs) return false
    if (supportedArch != other.supportedArch) return false
    if (includeInDistribution != other.includeInDistribution) return false
    return true
  }
}
