// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus

/**
 * Determines the [MinimapSupportLevel] for a file type.
 *
 * The minimap is intended for linear, scrollable text editors where a compressed visual overview
 * improves navigation. It is enabled by default for source code and code-adjacent text formats,
 * and extensible per IDE product.
 *
 * Implementations use a chain-of-responsibility pattern: return `null` to defer to the next
 * registered policy. Binary file types are always [MinimapSupportLevel.UNSUPPORTED] regardless
 * of what any implementation returns.
 *
 * Register via `com.intellij.minimapFileSupportPolicy` extension point.
 */
@ApiStatus.OverrideOnly
interface MinimapFileSupportPolicy {
  /**
   * Returns the support level for [fileType], or `null` to defer to the next policy in the chain.
   * Binary types are rejected before this method is called.
   */
  fun getSupportLevel(fileType: FileType): MinimapSupportLevel?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapFileSupportPolicy> =
      ExtensionPointName("com.intellij.minimapFileSupportPolicy")

    /**
     * Returns the effective [MinimapSupportLevel] for [fileType].
     *
     * Binary types unconditionally yield [MinimapSupportLevel.UNSUPPORTED].
     * Registered [MinimapFileSupportPolicy] implementations are consulted first (first non-null
     * result wins), falling back to [DefaultMinimapFileSupportPolicy].
     */
    fun forFileType(fileType: FileType): MinimapSupportLevel {
      if (fileType.isBinary) return MinimapSupportLevel.UNSUPPORTED
      for (policy in EP_NAME.extensionList) {
        val level = policy.getSupportLevel(fileType)
        if (level != null) return level
      }
      return DefaultMinimapFileSupportPolicy.getSupportLevel(fileType)
    }
  }
}
