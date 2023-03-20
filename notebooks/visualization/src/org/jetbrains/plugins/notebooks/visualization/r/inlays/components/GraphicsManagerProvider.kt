/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface GraphicsManagerProvider {
  fun getManager(project: Project): GraphicsManager

  companion object {
    private val EP = ExtensionPointName.create<GraphicsManagerProvider>("com.intellij.datavis.r.inlays.components.graphicsManagerProvider")

    fun getInstance(): GraphicsManagerProvider? {
      return EP.extensionList.firstOrNull()
    }
  }
}
