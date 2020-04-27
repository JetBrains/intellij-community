// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.ContentKey
import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.openapi.editor.Editor


/**
 * Root of the presentation tree, has explicit content ([Content])
 * This method is responsible for construction of actual presentation and must do it lazily (due to performance reasons).
 */
interface RootInlayPresentation<Content : Any> : InlayPresentation {
  /**
   * Method is called on old presentation to apply updates to its content.
   *
   * This action should be FAST, it executes inside write action!
   * This method is called only if [key]s of presentations are the same.
   *
   * @param newPresentationContent is a content of root of the NEW presentation
   * @return true, iff something is really changed
   */
  fun update(newPresentationContent: Content, editor: Editor, factory: InlayPresentationFactory): Boolean

  val content: Content

  val key: ContentKey<Content>
}