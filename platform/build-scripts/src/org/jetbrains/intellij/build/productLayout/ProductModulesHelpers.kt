// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/**
 * Product spec helper functions for reusable product content fragments.
 * These functions return ProductModulesContentSpec instances that can be composed using include().
 *
 * Use these helpers to reduce duplication across product specifications while keeping
 * ProductModulesContentSpec immutable and declarative (suitable for future YAML representation).
 */

/**
 * Common capability aliases shared across most ultimate products.
 * Bundles frequently repeated capability declarations to reduce duplication.
 *
 * Includes:
 * - Run targets support
 * - Microservices capabilities
 * - ML inline completion
 * - IDE provisioner
 * - Marketplace integration
 *
 * Usage:
 * ```
 * override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
 *   include(commonCapabilityAliases())
 *   // ...
 * }
 * ```
 */
fun commonCapabilityAliases(): ProductModulesContentSpec = productModules {
  alias("com.intellij.modules.run.targets")
  alias("com.intellij.modules.microservices")
  alias("com.intellij.ml.inline.completion")
  alias("com.intellij.platform.ide.provisioner")
  alias("com.intellij.marketplace")
}

/**
 * Python capability aliases for non-PyCharm products.
 * Enables Python support in multi-language IDEs like GoLand, DataGrip, CLion, RustRover, etc.
 *
 * Includes:
 * - Python core capabilities
 * - Python in mini-IDE capabilities
 * - Python in non-PyCharm IDE capabilities
 *
 * Usage:
 * ```
 * override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
 *   include(pythonMiniIdeCapabilities())
 *   // ...
 * }
 * ```
 */
fun pythonMiniIdeCapabilities(): ProductModulesContentSpec = productModules {
  alias("com.intellij.modules.python-core-capable")
  alias("com.intellij.modules.python-in-mini-ide-capable")
  alias("com.intellij.modules.python-in-non-pycharm-ide-capable")
}

/**
 * Common platform includes repeated across most ultimate products.
 * Bundles deprecatedInclude statements for legacy XML resource inclusion.
 *
 * Includes:
 * - Platform lang plugin resources
 * - Structural search resources
 * - Remote servers implementation
 * - Ultimate edition resources
 *
 * Usage:
 * ```
 * override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
 *   include(platformCommonIncludes())
 *   // ...
 * }
 * ```
 */
fun platformCommonIncludes(): ProductModulesContentSpec = productModules {
  deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
  deprecatedInclude("intellij.platform.structuralSearch", "META-INF/structuralsearch.xml")
  deprecatedInclude("intellij.platform.remoteServers.impl", "intellij.platform.remoteServers.impl.xml")
  deprecatedInclude("intellij.platform.commercial", "META-INF/ultimate.xml")
}

/**
 * Extensions for native development IDEs (CLion, GoLand, RustRover).
 * Combines process elevation support with native debugger capability.
 *
 * Includes:
 * - Process elevation module set (for operations requiring elevated privileges)
 * - Native debugger plugin capability alias
 *
 * Usage:
 * ```
 * override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
 *   include(nativeDevExtensions())
 *   // ...
 * }
 * ```
 */
fun nativeDevExtensions(): ProductModulesContentSpec = productModules {
  moduleSet(CommunityModuleSets.elevation())
  alias("com.intellij.modules.nativeDebug-plugin-capable")
}
