// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

/**
 * Describes how strongly a file type supports the minimap.
 *
 * The platform provides a default policy based on file type classification, and individual IDEs
 * can extend or override it via the [MinimapFileSupportPolicy] extension point.
 */
enum class MinimapSupportLevel {
  /** Minimap can be shown for this file type when minimap is available and enabled. */
  SUPPORTED,

  /** Minimap is never shown for this file type; not surfaced in settings. */
  UNSUPPORTED,
}
