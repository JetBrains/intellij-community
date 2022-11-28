// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Provider of declarative expandable/collapsible inlays.
 * Much faster and simpler than [com.intellij.codeInsight.hints.InlayHintsProvider].
 *
 * If you need to have support for multiple languages, use [com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory]
 */
interface InlayHintsProvider {
  companion object {
    @JvmStatic
    val PROVIDER_NAME: DataKey<@Nls String> = DataKey.create("declarative.hints.provider.name")
    @JvmStatic
    val PROVIDER_ID: DataKey<@NonNls String> = DataKey.create("declarative.hints.provider.id")
    @JvmStatic
    val INLAY_PAYLOADS: DataKey<Map<String, InlayActionPayload>> = DataKey.create("declarative.hints.inlay.payload")
  }

  /**
   * Creates collector for given file and editor if it may create inlays, or null otherwise.
   */
  fun createCollector(file: PsiFile, editor: Editor) : InlayHintsCollector?

  /**
   * Creates collector for preview (in settings).
   */
  fun createCollectorForPreview(file: PsiFile, editor: Editor) : InlayHintsCollector?
}