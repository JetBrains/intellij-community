// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

interface IdeRootPaneNorthExtension {
  companion object {
    @Internal
    @JvmField
    val EP_NAME: ExtensionPointName<IdeRootPaneNorthExtension> = ExtensionPointName("com.intellij.ideRootPaneNorth")
  }

  val key: String

  fun createComponent(project: Project, isDocked: Boolean): JComponent? {
    return null
  }

  @Experimental
  @Internal
  fun component(project: Project, isDocked: Boolean, statusBar: StatusBar): Flow<JComponent?>? {
    return null
  }
}