// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.openapi.fileTypes.FileType

/**
 * Platform-level default minimap support policy.
 *
 * All non-binary file types are [MinimapSupportLevel.SUPPORTED_BY_DEFAULT].
 * Binary types are rejected before this object is consulted; see [MinimapFileSupportPolicy.forFileType].
 *
 * IDEs that need to suppress the minimap for specific file types (e.g. notebooks that provide their own
 * minimap implementation) should register a [MinimapFileSupportPolicy] via the extension point and return
 * [MinimapSupportLevel.UNSUPPORTED] for the relevant types.
 */
internal object DefaultMinimapFileSupportPolicy : MinimapFileSupportPolicy {
  override fun getSupportLevel(fileType: FileType): MinimapSupportLevel = MinimapSupportLevel.SUPPORTED_BY_DEFAULT
}
