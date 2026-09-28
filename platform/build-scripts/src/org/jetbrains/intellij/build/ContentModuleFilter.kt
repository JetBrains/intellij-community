// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to exclude from the distribution some modules registered as an optional 'content module' in a plugin.
 * It can be used to reduce the installation size by removing modules which will never be compatible with it, e.g., for a standalone frontend variant of an IDE.
 */
@ApiStatus.Experimental
interface ContentModuleFilter {
  /**
   * Returns `true` if a module with name [moduleName] which is registered in `content` tag in `plugin.xml` should be added to the plugin distribution, and `false` otherwise.
   * If the function returns `false`, the distribution holds no jar of the module. Whether the `<module/>` tag also leaves `plugin.xml` is
   * decided by [keepsRefusedModuleInDescriptor].
   *
   * Note, that modules with `loading` rule set to `required` or `embedded` must be always included; this function will be called only for modules `optional` and `on-demand`
   * loading rules.
   * @param pluginMainModuleName specifies the plugin where [moduleName] is included, or `null` if [moduleName] is part of the core (platform) plugin
   */
  fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean

  /**
   * Whether a refused module of the plugin [pluginMainModuleName] keeps its `<module/>` tag and its embedded descriptor in `plugin.xml`.
   *
   * `true` for a filter that refuses a module the run time excludes anyway, such as the product mode filter: the run time reads the
   * descriptor, excludes the module, and never opens the jar that the distribution does not hold. `false` for a filter that refuses a
   * module the build cannot resolve: the tag leaves `plugin.xml`. A refused module of the core plugin (`pluginMainModuleName == null`)
   * always leaves it.
   */
  fun keepsRefusedModuleInDescriptor(pluginMainModuleName: String?): Boolean = false
}
