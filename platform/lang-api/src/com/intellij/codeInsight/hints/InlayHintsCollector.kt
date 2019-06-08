// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement


/**
 * Collector created once for collection session and reused for all session elements.
 * Implementors do NOT need to traverse element subtree themselves.
 */
interface InlayHintsCollector {
  /**
   * Explores [element] and adds some hints to [sink] if necessary.
   * Implementors must handle dumb mode themselves.
   * @return false if it is not necessary to traverse child elements (but implementors should not rely on it)
   */
  fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink) : Boolean
}
