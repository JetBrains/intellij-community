// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * @see org.jetbrains.intellij.build.ProductModulesLayout#setPluginModulesToPublish(java.util.List)
 * @see org.jetbrains.intellij.build.ProductModulesLayout#pluginsToPublish
 */
@CompileStatic
class PluginPublishingSpec {
  // The main module (containing META-INF/plugin.xml) of the plugin 
  String mainModule

  /**
   * Whether since-build/until-build range should be restricted.
   * NULL means, the compatibility build range will be automatically determined depending on the other parameters. 
   */
  CompatibleBuildRange compatibleBuildRange

  /**
   * If {@code true} the plugin will be added to the xml descriptor for the custom plugin repository.
   * See {@link org.jetbrains.intellij.build.ProductModulesLayout#prepareCustomPluginRepositoryForPublishedPlugins}.
   */
  boolean includeInCustomPluginRepository

  PluginPublishingSpec(String mainModule,
                       CompatibleBuildRange compatibleBuildRangeOrNullForDefault = null,
                       boolean includeInCustomPluginRepository = true) {
    this.mainModule = mainModule
    this.compatibleBuildRange = compatibleBuildRangeOrNullForDefault
    this.includeInCustomPluginRepository = includeInCustomPluginRepository
  }
}
