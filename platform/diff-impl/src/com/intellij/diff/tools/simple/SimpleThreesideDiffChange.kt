// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.diff.util.Side.Companion.fromLeft
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class SimpleThreesideDiffChange(
  fragment: MergeLineFragment,
  conflictType: MergeConflictType,
  override val innerFragments: MergeInnerDifferences?,
  private val myViewer: SimpleThreesideDiffViewer
) : ThreesideDiffChangeBase(conflictType) {
  private val myLineStarts = IntArray(3)
  private val myLineEnds = IntArray(3)

  var isValid: Boolean = true
    private set

  init {
    for (side in ThreeSide.entries) {
      myLineStarts[side.index] = fragment.getStartLine(side)
      myLineEnds[side.index] = fragment.getEndLine(side)
    }

    reinstallHighlighters()
  }

  @RequiresEdt
  fun reinstallHighlighters() {
    destroyHighlighters()
    installHighlighters()

    destroyInnerHighlighters()
    installInnerHighlighters()

    destroyOperations()
    installOperations()
  }

  override fun installOperations() {
    myOperations.add(createAcceptOperation(ThreeSide.LEFT, ThreeSide.BASE))
    myOperations.add(createAcceptOperation(ThreeSide.RIGHT, ThreeSide.BASE))
    myOperations.add(createAcceptOperation(ThreeSide.BASE, ThreeSide.LEFT))
    myOperations.add(createAcceptOperation(ThreeSide.BASE, ThreeSide.RIGHT))
  }

  //
  // Getters
  //
  override fun getStartLine(side: ThreeSide): Int = side.select(myLineStarts)
  override fun getEndLine(side: ThreeSide): Int = side.select(myLineEnds)

  override fun isResolved(side: ThreeSide): Boolean = false

  override fun getEditor(side: ThreeSide): Editor = myViewer.getEditor(side)

  fun markInvalid() {
    this.isValid = false

    destroyOperations()
  }

  //
  // Shift
  //
  fun processChange(oldLine1: Int, oldLine2: Int, shift: Int, side: ThreeSide): Boolean {
    val line1 = getStartLine(side)
    val line2 = getEndLine(side)
    val sideIndex = side.index

    val newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift)
    myLineStarts[sideIndex] = newRange.startLine
    myLineEnds[sideIndex] = newRange.endLine

    return newRange.damaged
  }

  private fun createAcceptOperation(sourceSide: ThreeSide, modifiedSide: ThreeSide): DiffGutterOperation {
    val editor = myViewer.getEditor(sourceSide)
    val offset = DiffGutterOperation.lineToOffset(editor, getStartLine(sourceSide))

    return DiffGutterOperation.Simple(editor, offset, DiffGutterOperation.RendererBuilder {
      val isOtherEditable = myViewer.isEditable(modifiedSide)
      if (!isOtherEditable) return@RendererBuilder null

      val isChanged = sourceSide != ThreeSide.BASE && isChange(sourceSide) ||
                      modifiedSide != ThreeSide.BASE && isChange(modifiedSide)
      if (!isChanged) return@RendererBuilder null

      val text: String = getApplyActionText(myViewer, sourceSide, modifiedSide)
      val arrowDirection = fromLeft(sourceSide == ThreeSide.LEFT ||
                                    modifiedSide == ThreeSide.RIGHT)
      val icon = DiffUtil.getArrowIcon(arrowDirection)
      createIconRenderer(modifiedSide, text, icon,
                         Runnable { myViewer.replaceChange(this, sourceSide, modifiedSide) })
    })
  }

  private fun createIconRenderer(
    modifiedSide: ThreeSide,
    tooltipText: @NlsContexts.Tooltip String,
    icon: Icon,
    perform: Runnable
  ): GutterIconRenderer {
    return object : DiffGutterRenderer(icon, tooltipText) {
      override fun handleMouseClick() {
        if (!isValid) return
        val project = myViewer.project
        val document: Document = myViewer.getEditor(modifiedSide).getDocument()
        DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.replace.change.command"), perform)
      }
    }
  }

  companion object {
    //
    // Modification
    //
    @JvmStatic
    fun getApplyActionText(
      viewer: DiffViewerBase,
      sourceSide: ThreeSide,
      modifiedSide: ThreeSide
    ): @Nls String {
      val key: Key<String>? = when {
        sourceSide == ThreeSide.BASE && modifiedSide == ThreeSide.LEFT -> {
          DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_LEFT_ACTION_TEXT
        }
        sourceSide == ThreeSide.BASE && modifiedSide == ThreeSide.RIGHT -> {
          DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_RIGHT_ACTION_TEXT
        }
        sourceSide == ThreeSide.LEFT && modifiedSide == ThreeSide.BASE -> {
          DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_TO_BASE_ACTION_TEXT
        }
        sourceSide == ThreeSide.RIGHT && modifiedSide == ThreeSide.BASE -> {
          DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_TO_BASE_ACTION_TEXT
        }
        else -> {
          null
        }
      }
      if (key != null) {
        val customValue = DiffUtil.getUserData<@Nls String>(viewer.request, viewer.context, key)
        if (customValue != null) return customValue
      }

      return DiffBundle.message("action.presentation.diff.accept.text")
    }
  }
}