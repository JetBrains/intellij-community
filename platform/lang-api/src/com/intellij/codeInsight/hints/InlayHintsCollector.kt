// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement


/**
 * Collects inlays in the given element (not recursively).
 */
interface InlayHintsCollector {
  /**
   * Implementors must handle dumb mode themselves.
   */
  fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink)
}
