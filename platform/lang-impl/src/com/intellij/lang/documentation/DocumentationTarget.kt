// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * The minimal entity which is needed for documentation actions.
 *
 * To provide [DocumentationTarget] implement and register:
 * - by an offset in a [file][com.intellij.psi.PsiFile] - [DocumentationTargetProvider];
 * - by a target [symbol][com.intellij.model.Symbol] - [SymbolDocumentationTargetProvider][com.intellij.lang.documentation.symbol.SymbolDocumentationTargetProvider] (or see other options in `SymbolDocumentationTargetProvider` docs);
 * - by a target [element][com.intellij.psi.PsiElement] - [PsiDocumentationTargetProvider][com.intellij.lang.documentation.psi.PsiDocumentationTargetProvider].
 */
@Experimental
@OverrideOnly
interface DocumentationTarget {

  /**
   * The current instance is valid within a single read action.
   * This function must be used to access the entity between different read actions.
   * See [Pointer] docs for an example.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun createPointer(): Pointer<out DocumentationTarget>

  /**
   * Returned presentation is used to render the tab name and icon in the tool window,
   * and to render location info under the documentation in the popup.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun presentation(): TargetPresentation

  @Suppress("DEPRECATION") // deprecated JvmDefault
  val navigatable: Navigatable?
    @RequiresReadLock
    @RequiresBackgroundThread
    get() = null

  /**
   * TODO consider extracting separate interface ShortDocumentationTarget
   * TODO consider showing full doc on ctrl+hover
   *
   * @return a HTML string to show in the editor hint when this target is highlighted by ctrl+mouse hover,
   * or `null` if this target doesn't need a hint
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun computeDocumentationHint(): @HintText String? = null

  /**
   * If the documentation can be computed in the current read action, then the implementation is expected to do so,
   * and return [DocumentationResult.documentation].
   * For example, the implementation may compute the documentation from a `PsiElement` which represents a comment.
   *
   * Otherwise, the function is supposed to obtain the necessary data inside the current read action,
   * capture this data into a computable instance, which will be executed later outside the read action,
   * and return this computable instance via [DocumentationResult.asyncDocumentation].
   * For example, the implementation may compute a URL by underlying `PsiElement`,
   * and then return a computable which will download the data outside the current read action.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun computeDocumentation(): DocumentationResult? = null
}
