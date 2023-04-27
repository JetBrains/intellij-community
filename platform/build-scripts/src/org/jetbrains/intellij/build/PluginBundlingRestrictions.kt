// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Allows to exclude a plugin from some distributions.
 *
 * @see org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#getBundlingRestrictions()
 */
class PluginBundlingRestrictions private constructor(
  /**
   * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
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
   * Set to `true` if the plugin should be included in distribution for EAP builds only.
   */
  @JvmField
  val includeInEapOnly: Boolean,
) {
  companion object {
    @JvmField
    val NONE = PluginBundlingRestrictions(OsFamily.ALL, JvmArchitecture.ALL, false)

    /**
     * Use if the plugin should be included anywhere, just used to calculate searchable options index, provided module list.
     * Must be used with conjunction of other PluginLayout's which do have actual restrictions.
     */
    @JvmField
    val EPHEMERAL = PluginBundlingRestrictions(persistentListOf(), persistentListOf(), false)

    /**
     * Use for the plugin version which is uploaded to marketplace, since marketplace does not support per-OS/ARCH plugins.
     * Bundled plugins must also have other PluginLayouts with different restrictions.
     *
     * If plugin is identical for both bundled and marketplace-uploaded versions, use [NONE] instead.
     */
    @JvmField
    val MARKETPLACE = PluginBundlingRestrictions(persistentListOf(), persistentListOf(), false)
  }

  class Builder {
    /**
     * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
     */
    var supportedOs: PersistentList<OsFamily> = OsFamily.ALL

    /**
     * Change this value if the plugin works on some architectures only and
     * therefore don't need to be bundled with distributions for other architectures.
     */
    var supportedArch: List<JvmArchitecture> = JvmArchitecture.ALL

    /**
     * Set to `true` if the plugin should be included in distribution for EAP builds only.
     */
    var includeInEapOnly: Boolean = false

    var ephemeral: Boolean = false

    var marketplace: Boolean = false

    internal fun build(): PluginBundlingRestrictions {
      if (ephemeral) {
        check(supportedOs == OsFamily.ALL)
        check(supportedArch == JvmArchitecture.ALL)
        check(!includeInEapOnly)
        return EPHEMERAL
      }
      if (marketplace) {
        check(supportedOs == OsFamily.ALL)
        check(supportedArch == JvmArchitecture.ALL)
        check(!includeInEapOnly)
        return MARKETPLACE
      }
      return when (val restrictions = PluginBundlingRestrictions(supportedOs, supportedArch, includeInEapOnly)) {
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

  override fun toString(): String {
    if (this === EPHEMERAL) return "ephemeral"
    if (this === MARKETPLACE) return "marketplace"
    if (this == NONE) return "unrestricted"
    return "os: ${if (supportedOs == OsFamily.ALL) "unrestricted" else supportedOs.joinToString(",")}, " +
           "arch: ${if (supportedArch == JvmArchitecture.ALL) "unrestricted" else supportedArch.joinToString(",")}, " +
           "eapOnly: $includeInEapOnly"
  }

  override fun hashCode(): Int {
    if (this === EPHEMERAL) return -1
    if (this === MARKETPLACE) return -2

    var result = supportedOs.hashCode()
    result = 31 * result + supportedArch.hashCode()
    result = 31 * result + includeInEapOnly.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginBundlingRestrictions

    if (this === EPHEMERAL) return false
    if (this === MARKETPLACE) return false

    if (supportedOs != other.supportedOs) return false
    if (supportedArch != other.supportedArch) return false
    return includeInEapOnly == other.includeInEapOnly
  }
}
