// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.InlayHint
import org.jetbrains.annotations.ApiStatus

sealed class LspInlayHintCustomizer

/**
 * Base class for customizing LSP inlay hints behavior in language plugins.
 *
 * This is the primary integration point for language plugins that want to provide
 * settings and customization for LSP inlay hints. Language plugins should extend
 * this class and override the relevant methods to implement their own settings logic.
 */
open class LspInlayHintSupport : LspInlayHintCustomizer() {
  /**
   * Determines whether inlay hints should be processed for the given file.
   * This is called before processing individual hint types.
   *
   * @param file the file to check for inlay hints processing
   * @return true if inlay hints should be processed for this file, false otherwise
   */
  open fun shouldAskServerForInlayHints(file: VirtualFile): Boolean = true

  /**
   * Determines whether a specific inlay hint should be processed and displayed.
   * This method provides access to the full InlayHint object, allowing custom
   * filtering logic based on label, kind, or any other hint properties.
   *
   * @param inlayHint the LSP InlayHint object containing all hint information
   * @return true if the hint should be processed and displayed, false to filter it out
   *
   * @deprecated Use [shouldDisplayInlayHint] instead
   */
  @Deprecated("Use shouldDisplayInlayHint(file: VirtualFile, inlayHint: InlayHint) instead")
  @ApiStatus.ScheduledForRemoval
  open fun shouldApplyInlayHint(inlayHint: InlayHint): Boolean = true

  /**
   * Determines whether a specific inlay hint should be processed and displayed.
   * This method provides access to the full InlayHint object, allowing custom
   * filtering logic based on label, kind, or any other hint properties.
   *
   * @param file the file for which the hint is being processed
   * @param inlayHint the LSP InlayHint object containing all hint information
   * @return true if the hint should be processed and displayed, false to filter it out
   */
  open fun shouldDisplayInlayHint(file: VirtualFile, inlayHint: InlayHint): Boolean =
    @Suppress("DEPRECATION")
    shouldApplyInlayHint(inlayHint)

  /**
   * Maximum number of characters allowed for a single inlay hint label.
   * Applies to both simple (string) and composite labels (sum of parts).
   * Values are sanitized by the platform to a safe range.
   */
  open fun getMaxInlayHintChars(): Int = 42
}

object LspInlayHintDisabled : LspInlayHintCustomizer()
