// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

/**
 * Specified when a content module of a plugin should be loaded.
 */
@ApiStatus.Internal
enum class ModuleLoadingRule(val required: Boolean) {
  /** 
   * Module is the required part of the plugin. 
   * If the module cannot be loaded because some of its dependencies aren't available, the whole plugin isn't loaded, and an error is shown to the user.
   * Classes from the module content descriptor will be loaded by a separate classloader.
   */                           
  REQUIRED(required = true),

  /**
   * The same as [REQUIRED], but classes from the module content descriptor will be loaded by the main plugin classloader.
   * Use this option only for existing modules which are highly coupled with other required modules, where using a separate classloader breaks behavior or compatibility with other
   * plugins. 
   * For new modules, use [REQUIRED] instead.
   */
  EMBEDDED(required = true),

  /**
   * Module is an optional part of the plugin. If the module cannot be loaded, it is skipped and doesn't prevent other modules from the plugin from being loaded.
   * Classes from the module content descriptor will be loaded by a separate classloader. 
   */
  OPTIONAL(required = false),

  /**
   * Module is used by other modules of the plugin and doesn't provide user-visible functionality itself. 
   * The module is loaded if and only if other `required` or `optional` module which depend on it is loaded.
   * Classes from the module content descriptor will be loaded by a separate classloader.
   * This *isn't implemented yet* and currently treated the same way as [OPTIONAL].
   */
  ON_DEMAND(required = false);
}