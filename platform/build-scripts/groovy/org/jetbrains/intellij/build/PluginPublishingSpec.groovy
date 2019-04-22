// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Specifies how a plugin is published.
 *
 * @see org.jetbrains.intellij.build.ProductModulesLayout#setPluginModulesToPublish
 * @see org.jetbrains.intellij.build.ProductModulesLayout#setPluginPublishingSpec
 */
@CompileStatic
class PluginPublishingSpec {
  public static final PluginPublishingSpec DO_NOT_UPLOAD_AUTOMATICALLY = new PluginPublishingSpec(includeIntoDirectoryForAutomaticUploading: false)

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

  /**
   * If {@code true} the plugin distribution will be added to "auto-uploading" subdirectory in "&lt;product-code&gt;-plugins" directory in
   * the artifacts directory. All plugins from that directory are supposed to be automatically uploaded to plugins.jetbrains.com.
   */
  boolean includeIntoDirectoryForAutomaticUploading

  PluginPublishingSpec(CompatibleBuildRange compatibleBuildRangeOrNullForDefault = null,
                       boolean includeInCustomPluginRepository = true,
                       boolean includeIntoDirectoryForAutomaticUploading = false) {
    this.compatibleBuildRange = compatibleBuildRangeOrNullForDefault
    this.includeInCustomPluginRepository = includeInCustomPluginRepository
    this.includeIntoDirectoryForAutomaticUploading = includeIntoDirectoryForAutomaticUploading
  }
}
