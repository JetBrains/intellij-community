// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * @see org.jetbrains.intellij.build.ProductModulesLayout#setPluginModulesToPublish
 * @see org.jetbrains.intellij.build.ProductModulesLayout#setPluginPublishingSpec
 */
@CompileStatic
class PluginPublishingSpec {
  /**
   * Whether since-build/until-build range should be restricted.
   * {@code null} means, the compatibility build range will be automatically determined depending on the other parameters. 
   */
  CompatibleBuildRange compatibleBuildRange

  /**
   * If {@code true} the plugin will be added to the xml descriptor for the custom plugin repository.
   * See {@link org.jetbrains.intellij.build.ProductModulesLayout#prepareCustomPluginRepositoryForPublishedPlugins}.
   */
  boolean includeInCustomPluginRepository

  PluginPublishingSpec(CompatibleBuildRange compatibleBuildRangeOrNullForDefault = null,
                       boolean includeInCustomPluginRepository = true) {
    this.compatibleBuildRange = compatibleBuildRangeOrNullForDefault
    this.includeInCustomPluginRepository = includeInCustomPluginRepository
  }
}
