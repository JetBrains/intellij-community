// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class SimpleDiffViewerHighlighters(
  override val change: SimpleThreesideDiffChange,
  innerFragments: MergeInnerDifferences?,
  private val viewer: SimpleThreesideDiffViewer,
) : DiffViewerHighlighters(change, innerFragments, viewer::getEditor) {
  init {
    reinstallAll()
  }

  override fun installOperations() {
    addOperation(createAcceptOperation(ThreeSide.LEFT, ThreeSide.BASE))
    addOperation(createAcceptOperation(ThreeSide.RIGHT, ThreeSide.BASE))
    addOperation(createAcceptOperation(ThreeSide.BASE, ThreeSide.LEFT))
    addOperation(createAcceptOperation(ThreeSide.BASE, ThreeSide.RIGHT))
  }

  private fun createAcceptOperation(sourceSide: ThreeSide, modifiedSide: ThreeSide): DiffGutterOperation {
    val editor = editorProvider(sourceSide)
    val offset = DiffGutterOperation.lineToOffset(editor, change.getStartLine(sourceSide))

    return DiffGutterOperation.Simple(editor, offset, DiffGutterOperation.RendererBuilder {
      val isOtherEditable = viewer.isEditable(modifiedSide)
      if (!isOtherEditable) return@RendererBuilder null

      val isChanged = sourceSide != ThreeSide.BASE && change.isChange(sourceSide) ||
                      modifiedSide != ThreeSide.BASE && change.isChange(modifiedSide)
      if (!isChanged) return@RendererBuilder null

      val text: String = getApplyActionText(viewer, sourceSide, modifiedSide)
      val arrowDirection = Side.fromLeft(sourceSide == ThreeSide.LEFT || modifiedSide == ThreeSide.RIGHT)
      val icon = DiffUtil.getArrowIcon(arrowDirection)
      createIconRenderer(modifiedSide, text, icon,
                         Runnable { viewer.replaceChange(change, sourceSide, modifiedSide) })
    })
  }

  private fun createIconRenderer(
    modifiedSide: ThreeSide,
    tooltipText: @NlsContexts.Tooltip String,
    icon: Icon,
    perform: Runnable,
  ): GutterIconRenderer {
    return object : DiffGutterRenderer(icon, tooltipText) {
      override fun handleMouseClick() {
        if (!change.isValid) return
        val project = viewer.project
        val document: Document = viewer.getEditor(modifiedSide).getDocument()
        DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.replace.change.command"), perform)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getApplyActionText(
      viewer: DiffViewerBase,
      sourceSide: ThreeSide,
      modifiedSide: ThreeSide,
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
        else -> null
      }
      if (key != null) {
        val customValue = DiffUtil.getUserData<@Nls String>(viewer.request, viewer.context, key)
        if (customValue != null) return customValue
      }

      return DiffBundle.message("action.presentation.diff.accept.text")
    }
  }
}