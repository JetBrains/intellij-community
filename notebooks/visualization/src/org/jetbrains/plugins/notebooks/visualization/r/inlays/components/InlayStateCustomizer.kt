/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental

/** This extension point allows to perform additional customizations to inlay outputs before adding
 * them to a notebook.
 *
 * @see NotebookInlayState
 * */
@Experimental
interface InlayStateCustomizer {
  /** Checks if a customizer can customize an inlay output of the given type. */
  fun <T : NotebookInlayState> isApplicableTo(klass: Class<T>): Boolean = true

  /** Applies customizations and return the inlay output. */
  fun customize(state: NotebookInlayState): NotebookInlayState

  companion object {
    val EP = ExtensionPointName.create<InlayStateCustomizer>("com.intellij.datavis.inlays.components.inlayStateCustomizer")
  }
}