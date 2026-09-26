// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

/**
 * Modification means modification direction (inside or outside IDEA).
 */
enum class ExternalSystemModificationType {

  /**
   * Modification which detected by outside events IDEA.
   * For example modification in OS file system or modifications from external tools.
   */
  EXTERNAL,

  /**
   * Modification which detected by inside events IDEA.
   * For example modification in editor or modifications from IDEA plugins.
   */
  INTERNAL,

  /**
   * Semantically, it is almost identical to [INTERNAL], but if this event occurs,
   * the project will not be automatically reloaded, even if the auto-reload type
   * selected is "Any changes".
   */
  HIDDEN,

  /**
   * Unknown type of modifications.
   */
  UNKNOWN;
}