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
 * The entity is valid within a read action, [createPointer] must be used to access the entity between different read actions.
 */
@Experimental
@OverrideOnly
interface DocumentationTarget {

  @RequiresReadLock
  @RequiresBackgroundThread
  fun createPointer(): Pointer<out DocumentationTarget>

  /**
   * Returned presentation is used to render the tab name and icon in the tool window,
   * and to render location info under the documentation in the popup.
   */
  val presentation: TargetPresentation
    @RequiresReadLock
    @RequiresBackgroundThread
    get

  @Suppress("DEPRECATION") // deprecated JvmDefault
  @JvmDefault
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
  @JvmDefault
  fun computeDocumentationHint(): @HintText String? = null

  /**
   * If the documentation can be computed in the current read action, then the implementation is expected to do so,
   * and return [DocumentationResult.documentation] or [DocumentationResult.externalDocumentation].
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
  @JvmDefault
  fun computeDocumentation(): DocumentationResult? = null
}
