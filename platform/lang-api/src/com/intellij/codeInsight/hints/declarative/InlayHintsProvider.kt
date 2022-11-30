// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Provider of declarative expandable/collapsible inlays.
 * Much faster and simpler than [com.intellij.codeInsight.hints.InlayHintsProvider].
 *
 * If you need to have support for multiple languages, use [com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory]
 */
interface InlayHintsProvider {
  /**
   * Creates collector for given file and editor if it may create inlays, or null otherwise.
   */
  fun createCollector(file: PsiFile, editor: Editor) : InlayHintsCollector?

  /**
   * Creates collector for preview (in settings).
   */
  fun createCollectorForPreview(file: PsiFile, editor: Editor) : InlayHintsCollector?
}