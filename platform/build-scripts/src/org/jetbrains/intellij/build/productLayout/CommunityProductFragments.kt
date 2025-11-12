// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/**
 * Registry of reusable product content fragments for community products.
 * These fragments bundle commonly repeated patterns into reusable units.
 */
object CommunityProductFragments {
  /**
   * Platform Lang base fragment: includes PlatformLangPlugin.xml.
   * 
   * PlatformLangPlugin.xml provides core platform language support including:
   * - Platform language components and extensions
   * - Core actions, refactoring, and editor support
   * 
   * Use this fragment instead of manually calling deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml").
   */
  fun platformLangBaseFragment(): ProductModulesContentSpec = productModules {
    deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
  }

  /**
   * Java IDE base fragment: provides Java IDE module aliases and optional plugin includes.
   *
   * Includes:
   * - Module aliases for Java IDE capability detection
   * - Optional remote servers support
   * - Optional UI Designer support
   * - Extensions for IDEA-specific customization (UTM tracking, new UI onboarding)
   *
   * Use this fragment for products that include Java IDE functionality.
   */
  fun javaIdeBaseFragment(): ProductModulesContentSpec = productModules {
    // Module capability aliases
    alias("com.intellij.modules.all")
    alias("com.intellij.modules.jsp.base")

    // Optional plugin support (with fallback)
    deprecatedInclude("intellij.platform.remoteServers.impl", "intellij.platform.remoteServers.impl.xml", optional = true)
    deprecatedInclude("intellij.uiDesigner", "META-INF/DesignerCorePlugin.xml", optional = true)

    // Extensions block (UTM tracking, new UI onboarding)
    deprecatedInclude("intellij.java.ide.resources", "META-INF/JavaIdePlugin.xml")
  }
}
