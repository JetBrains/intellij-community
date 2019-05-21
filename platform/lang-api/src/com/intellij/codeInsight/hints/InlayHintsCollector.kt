// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement


/**
 * Collects inlays in the given element (not recursively).
 */
interface InlayHintsCollector<T : Any> {
  /**
   * Collect hints into some collection stored in this collector
   * Implementors must handle dumb mode themselves.
   * @param isEnabled provider is enabled
   */
  fun collect(element: PsiElement, editor: Editor, settings: T, isEnabled: Boolean, sink: InlayHintsSink)

  /**
   * Settings key of corresponding [InlayHintsProvider]
   */
  val key: SettingsKey<T>
}
