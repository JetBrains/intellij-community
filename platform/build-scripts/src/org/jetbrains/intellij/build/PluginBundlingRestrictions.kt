// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.Objects

/**
 * Allows excluding a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
class PluginBundlingRestrictions(
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
   * See [PluginDistribution]
   */
  @JvmField
  var includeInDistribution: PluginDistribution = PluginDistribution.ALL
) {
  companion object {
    @JvmField
    val NONE: PluginBundlingRestrictions = PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, PluginDistribution.ALL)

    /**
     * Use for the plugin version which is uploaded to the Marketplace, since the latter does not support per-OS/ARCH plugins.
     * Bundled plugins must also have other PluginLayouts with different restrictions.
     *
     * If bundled and marketplace-uploaded versions of the plugin are identical, use [NONE] instead.
     */
    @JvmField
    val MARKETPLACE: PluginBundlingRestrictions = PluginBundlingRestrictions(persistentListOf(), persistentListOf(), PluginDistribution.ALL)
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

    /**
     * See [PluginDistribution]
     */
    var includeInDistribution: PluginDistribution = PluginDistribution.ALL

    var marketplace: Boolean = false

    internal fun build(): PluginBundlingRestrictions {
      if (marketplace) {
        check(supportedOs == OsFamily.ALL)
        check(supportedArch == JvmArchitecture.ALL)
        check(includeInDistribution == PluginDistribution.ALL)
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
