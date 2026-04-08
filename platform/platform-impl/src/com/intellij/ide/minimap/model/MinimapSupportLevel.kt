// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

/**
 * Describes how strongly a file type supports the minimap.
 *
 * The platform provides a default policy based on file type classification, and individual IDEs
 * can extend or override it via the [MinimapFileSupportPolicy] extension point.
 */
enum class MinimapSupportLevel {
  /** Minimap is shown by default; user can disable per file type. */
  SUPPORTED_BY_DEFAULT,

  /** Minimap is not shown by default; user can enable per file type. */
  SUPPORTED_OPTIONAL,

  /** Minimap is never shown for this file type; not surfaced in settings. */
  UNSUPPORTED,
}
