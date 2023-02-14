// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

sealed interface InlayHintsCollector

/**
 * Preferable collector if it is required to get inlays for each element of the file.
 * The order of bypass is not specified.
 * You may not rely on the fact that every element of the file will actually be bypassed.
 * The collector should be stateless.
 */
interface SharedBypassCollector : InlayHintsCollector {
  /**
   * Collects inlays for a given element.
   */
  fun collectFromElement(element: PsiElement, sink: InlayTreeSink)
}

/**
 * Collector which may be used if it is not required to bypass elements, but e.g. to get the information from the server.
 */
interface OwnBypassCollector : InlayHintsCollector {
  /**
   * Collects all inlays for a given file.
   */
  fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink)

  /**
   * @return true iff this particular place (e.g. element near the caret) may contribute inlays if the provider is enabled.
   * It will be called inside intention, make sure it runs fast.
   */
  fun shouldSuggestToggling(project: Project, editor: Editor, file: PsiFile) : Boolean = false

  /**
   * @return true iff this particular place (e.g. element near the caret) may contribute inlays if the provider and particular set of options are enabled.
   * It will be called inside intention, make sure it runs fast.
   */
  fun getOptionsToToggle(project: Project, editor: Editor, file: PsiFile) : Set<String> = emptySet()
}