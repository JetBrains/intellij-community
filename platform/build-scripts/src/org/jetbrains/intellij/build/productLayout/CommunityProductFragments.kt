// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/**
 * Registry of reusable product content fragments for community products.
 * These fragments bundle commonly repeated patterns into reusable units.
 */
object CommunityProductFragments {
  /**
   * Java IDE base fragment: provides Java IDE module aliases and optional plugin includes.
   *
   * Includes:
   * - PlatformLangPlugin.xml for platform language support
   * - Module aliases for Java IDE capability detection
   * - Optional remote servers support
   * - Optional UI Designer support
   * - Extensions for IDEA-specific customization (UTM tracking, new UI onboarding)
   *
   * Use this fragment for products that include Java IDE functionality.
   */
  fun javaIdeBaseFragment(): ProductModulesContentSpec = productModules {
    deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")

    // Module capability aliases
    alias("com.intellij.modules.all")
    alias("com.intellij.modules.jsp.base")

    // Optional plugin support (with fallback)
    deprecatedInclude("intellij.platform.remoteServers.impl", "intellij.platform.remoteServers.impl.xml", optional = true)
    deprecatedInclude("intellij.uiDesigner", "META-INF/DesignerCorePlugin.xml", optional = true)

    // Extensions block (UTM tracking, new UI onboarding)
    deprecatedInclude("intellij.java.ide.resources", "META-INF/JavaIdePlugin.xml")
  }

  /**
     * PyCharm Core fragment: provides PyCharm-specific platform extensions.
     *
     * Includes:
     * - PlatformLangPlugin.xml for platform language support
     * - Module capability alias for PyCharm
     * - Optional remote servers support
     * - PyCharm-specific extensions and actions (via pycharm-core.xml)
     *
     * Use this fragment for products that include PyCharm functionality (PyCharm Community, PyCharm Professional, DataSpell).
     * Note: The extensions and actions blocks remain in pycharm-core.xml as they cannot be represented in the product content DSL.
     */
  fun pycharmCoreFragment(): ProductModulesContentSpec = productModules {
    // Include platform lang base (PyCharm requires platform language support)
    deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")

    // Module capability alias
    alias("com.intellij.modules.pycharm")

    // Optional remote servers support
    deprecatedInclude("intellij.platform.remoteServers.impl", "intellij.platform.remoteServers.impl.xml", optional = true)

    // Extensions and actions block (PyCharm-specific customization)
    deprecatedInclude("intellij.pycharm.community", "META-INF/pycharm-core.xml")
  }
}
