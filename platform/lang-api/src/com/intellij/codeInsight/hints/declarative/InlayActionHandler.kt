// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Handler for inlay clicks (must be clicked while holding ctrl).
 * It can be found by id,
 * which is provided during the construction of inlay in [com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder].
 * Must have reasonable equals.
 * Otherwise, it will be required to replace the corresponding tree element each time inlay provider is run.
 */
interface InlayActionHandler {
  companion object {
    private val EP = ExtensionPointName<InlayActionHandlerBean>("com.intellij.codeInsight.inlayActionHandler")
    fun getActionHandler(handlerId: String) : InlayActionHandler? {
      return EP.findFirstSafe { it.handlerId == handlerId }?.instance
    }
  }

  /**
   * Handles click on the corresponding inlay entry. Payload is provided by the entry.
   */
  @RequiresEdt
  fun handleClick(editor: Editor, payload: InlayActionPayload)
}