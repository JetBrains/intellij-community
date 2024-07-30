/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.InlayProgressStatus
import javax.swing.JLayeredPane

/** Base class for all NotebookInlay states. Inlay could be Data(Table/Chart) or Output(text/html) */
abstract class NotebookInlayState : JLayeredPane() {

  /**
   * An inlay component can adjust itself to fit the output.
   * We need callback so a component can return the height delayed.
   */
  var onHeightCalculated: ((height: Int) -> Unit)? = null

  abstract fun clear()

  /** Short description of inlay content. */
  abstract fun getCollapsedDescription(): String

  open fun updateProgressStatus(progressStatus: InlayProgressStatus) {}

  open fun onViewportChange(isInViewport: Boolean) {
    // Do nothing by default
  }
}
