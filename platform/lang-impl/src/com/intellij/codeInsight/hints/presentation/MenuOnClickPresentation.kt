// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * It is expected to be top level presentation, which have all the context to fill context menu
 */
class MenuOnClickPresentation(
  presentation: InlayPresentation,
  val project: Project,
  val actionsSupplier: () -> List<AnAction>
) : StaticDelegatePresentation(presentation) {
  override fun mouseClicked(event: MouseEvent, translated: Point) {
    super.mouseClicked(event, translated)
    if (SwingUtilities.isRightMouseButton(event) && !SwingUtilities.isLeftMouseButton(event)) {
      val actions = actionsSupplier()
      if (actions.isEmpty()) return
      JBPopupMenu.showByEvent(event, "InlayMenu", DefaultActionGroup(actions))
    }
  }
}