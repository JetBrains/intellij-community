/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.border.Border

abstract class BorderlessDialogWrapper(project: Project, @Nls private val dialogTitle: String, ideModalityType: IdeModalityType) :
  DialogWrapper(project, null, true, ideModalityType, false)
{
  override fun init() {
    super.init()
    title = dialogTitle
  }

  override fun createActions(): Array<Action> {
    return if (SystemInfo.isMac) arrayOf(cancelAction, okAction) else arrayOf(okAction, cancelAction)
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  protected fun createOkCancelPanel(): JComponent {
    val buttons = createActions().map { createJButtonForAction(it) }
    return createButtonsPanel(buttons)
  }
}
