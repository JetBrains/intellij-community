// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

/**
 * Describes visibility of a content module which controls where the module can be used as a dependency
 */
@ApiStatus.Internal
enum class ModuleVisibility {
  /**
   * Indicates that the module is visible only inside the plugin it's declared
   */
  PRIVATE,

  /**
   * Indicates that the module is visible only inside the modules which are declared in the same namespace.
   */
  INTERNAL,

  /**
   * Indicates that the module is visible from any module of any plugin.
   */
  PUBLIC,
}