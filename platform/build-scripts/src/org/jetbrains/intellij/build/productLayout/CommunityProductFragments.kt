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
}
