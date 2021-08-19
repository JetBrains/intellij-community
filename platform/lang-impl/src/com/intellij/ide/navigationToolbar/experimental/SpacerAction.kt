// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

private val BIG_SPACER_INTERNAL = ActionsBundle.message("big.spacer")
private val SMALL_SPACER_INTERNAL = ActionsBundle.message("small.spacer")
private val TINY_SPACER_INTERNAL = ActionsBundle.message("tiny.spacer")

@Suppress("HardCodedStringLiteral")
open class SpacerAction(val id: String, val space: Int) : DumbAwareAction(id), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return JPanel().apply {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      minimumSize = Dimension(space, space)
      preferredSize = Dimension(space, space)
      maximumSize = Dimension(space, space)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
  }
}

class TinySpacerAction : SpacerAction(TINY_SPACER_INTERNAL, JBUI.scale(4))
class SmallSpacerAction : SpacerAction(SMALL_SPACER_INTERNAL, JBUI.scale(6))
class BigSpacerAction : SpacerAction(BIG_SPACER_INTERNAL, JBUI.scale(8))
