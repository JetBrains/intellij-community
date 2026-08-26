// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/**
 * Registry of reusable product content fragments for community products.
 * These fragments bundle commonly repeated patterns into reusable units.
 */
object CommunityProductFragments {
  /**
   * Platform core fragment: provides the core plugin aliases.
   *
   * Includes:
   * - The `com.intellij.modules.platform`, `com.intellij.modules.lang`, and `com.intellij.modules.lang.actions` module aliases
   *
   * The shared core action sets are not included here; `CoreActionsPrelude` loads them at runtime.
   *
   * Use this fragment in every product; it replaces the retired PlatformLangPlugin.xml include.
   */
  fun platformCoreFragment(): ProductModulesContentSpec = productModules {
    // Module capability aliases
    alias("com.intellij.modules.platform")
    alias("com.intellij.modules.lang")
    alias("com.intellij.modules.lang.actions")
  }

  /**
   * Java IDE base fragment: provides Java IDE module aliases and optional plugin includes.
   *
   * Includes:
   * - The platform core fragment for platform language support
   * - Module aliases for Java IDE capability detection
   * - JSP base modules used by the Java plugin's JSP support
   * - Optional remote servers support
   * - Optional UI Designer support
   * - Extensions for IDEA-specific customization (UTM tracking, new UI onboarding)
   *
   * Use this fragment for products that include Java IDE functionality.
   */
  fun javaIdeBaseFragment(): ProductModulesContentSpec = productModules {
    include(platformCoreFragment())

    // Module capability aliases
    alias("com.intellij.modules.all")
    alias("com.intellij.modules.jsp.base")

    moduleSet(CommunityModuleSets.jspBase())

    // Optional plugin support
    module("intellij.platform.remoteServers.impl")
    deprecatedInclude("intellij.uiDesigner", "META-INF/DesignerCorePlugin.xml", optional = true)

    // Extensions block (UTM tracking, new UI onboarding)
    deprecatedInclude("intellij.java.ide.resources", "META-INF/JavaIdePlugin.xml")
  }

  /**
   * PyCharm Core fragment: provides PyCharm-specific platform extensions.
   *
   * Includes:
   * - The platform core fragment for platform language support
   * - Module capability alias for PyCharm
   * - Optional remote servers support
   * - PyCharm-specific extensions and actions (via pycharm-core.xml)
   *
   * Use this fragment for products that include PyCharm functionality (PyCharm Community, PyCharm Professional).
   * Note: The extensions and actions blocks remain in pycharm-core.xml as they cannot be represented in the product content DSL.
   */
  fun pycharmCoreFragment(): ProductModulesContentSpec = productModules {
    // Include the platform core base (PyCharm requires platform language support)
    include(platformCoreFragment())

    // Module capability alias
    alias("com.intellij.modules.pycharm")

    // Optional remote servers support
    module("intellij.platform.remoteServers.impl")

    // Extensions and actions block (PyCharm-specific customization)
    deprecatedInclude("intellij.pycharm.community", "META-INF/pycharm-core.xml")
  }
}
