/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.project.Project
import java.io.File

interface GraphicsManager {
  /**
   * Whether a [GraphicsPanel] should invert images for a dark editor
   */
  val isDarkModeEnabled: Boolean

  /**
   * Test whether the color palette of this [image] can be inverted by a [GraphicsPanel]
   */
  fun isInvertible(image: File): Boolean

  companion object {
    fun getInstance(project: Project): GraphicsManager? {
      return GraphicsManagerProvider.getInstance()?.getManager(project)
    }
  }
}
