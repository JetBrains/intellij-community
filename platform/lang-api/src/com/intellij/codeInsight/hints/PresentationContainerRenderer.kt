// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.codeInsight.hints.presentation.PresentationListener
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay

/**
 * Renderer, that contains a group of [RootInlayPresentation] inside and shows them according to some positioning strategy (e. g. by priority)
 */
interface PresentationContainerRenderer<Constraints: Any> : EditorCustomElementRenderer, InputHandler {
  fun setListener(listener: PresentationListener)

  fun addOrUpdate(new: List<ConstrainedPresentation<*, Constraints>>, editor: Editor, factory: InlayPresentationFactory)

  fun isAcceptablePlacement(placement: Inlay.Placement): Boolean
}