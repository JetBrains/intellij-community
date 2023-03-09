// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent

interface IdeRootPaneNorthExtension {
  companion object {
    val EP_NAME: ExtensionPointName<IdeRootPaneNorthExtension> = ExtensionPointName("com.intellij.ideRootPaneNorth")
  }

  val key: String

  fun createComponent(project: Project, isDocked: Boolean): JComponent?
}