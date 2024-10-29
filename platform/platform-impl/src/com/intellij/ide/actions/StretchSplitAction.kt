// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.actions.StretchSplitAction.StretchDirection.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.ComponentUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component


/**
 * Actions for changing editor splitter proportions
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
abstract class StretchSplitAction(private val direction: StretchDirection) : DumbAwareAction() {

  enum class StretchDirection {
    TOP, LEFT, BOTTOM, RIGHT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    stretch(editor, direction)
  }

  private fun stretch(editor: Editor, direction: StretchDirection) {
    val pixelSplitter = findSplitter(editor)!!
    when (direction) {
      TOP -> moveDivider(pixelSplitter, true)
      LEFT -> moveDivider(pixelSplitter, true)
      BOTTOM -> moveDivider(pixelSplitter, false)
      RIGHT -> moveDivider(pixelSplitter, false)
    }
  }

  private fun moveDivider(pixelSplitter: Splitter, shrinkFirstComponent: Boolean) {
    //The action stretches/shrinks focused component for this amount of pixels
    val step = 50
    //Protects of situations when one part of the splitter becomes invisible
    val delta = 10
    val minSize = step + delta
    val totalLength = pixelSplitter.firstComponentLength + pixelSplitter.secondComponentLength
    if (shrinkFirstComponent) {
      if (pixelSplitter.firstComponentLength > minSize) {
        pixelSplitter.proportion = (pixelSplitter.firstComponentLength - step).toFloat() / totalLength
      }
    } else {
      if (pixelSplitter.secondComponentLength > minSize) {
        pixelSplitter.proportion = (pixelSplitter.firstComponentLength + step).toFloat() / totalLength
      }
    }
  }

  private fun findSplitter(editor: Editor): Splitter? {
    val editorComponent = editor.component
    val pixelSplitter = ComponentUtil.findParentByCondition(editorComponent) {
      p: Component? -> p is Splitter
                       && p.isVertical == (direction == TOP || direction == BOTTOM)
                       && (p.proportion > 0 && p.proportion < 1)
    }
    if (pixelSplitter is Splitter) {
      return pixelSplitter
    }
    return null
  }


  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val enabled = editor != null && findSplitter(editor) != null
    e.presentation.isEnabled = enabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  class StretchToTop: StretchSplitAction(TOP)
  class StretchToLeft: StretchSplitAction(LEFT)
  class StretchToBottom: StretchSplitAction(BOTTOM)
  class StretchToRight: StretchSplitAction(RIGHT)

}

private val Splitter.firstComponentLength: Int
  get() {
    return if (isVertical) firstComponent.height else firstComponent.width
  }

private val Splitter.secondComponentLength: Int
  get() {
    return if (isVertical) secondComponent.height else secondComponent.width
  }

