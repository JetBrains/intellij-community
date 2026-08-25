// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.frontend.FrontendDiffContent
import com.intellij.diff.frontend.FrontendDiffContext
import com.intellij.diff.frontend.FrontendDiffExtension
import com.intellij.diff.frontend.FrontendDiffRequest
import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Internal
class FrontendDiffDebugExtension : FrontendDiffExtension {
  override fun install(
    context: FrontendDiffContext,
    request: FrontendDiffRequest,
    viewer: FrontendDiffViewer,
    disposable: Disposable,
  ) {
    if (!RegistryManager.getInstance().`is`(REGISTRY_KEY)) return

    val inlays = viewer.sidedEditors().mapNotNull { sidedEditor ->
      createInlay(viewer, sidedEditor, request.contents, disposable)
    }

    viewer.addActualStateListener(disposable) {
      ApplicationManager.getApplication().invokeLater(
        {
          for (debugInlay in inlays) {
            if (!debugInlay.inlay.isValid) continue
            debugInlay.update(buildDebugLines(viewer, debugInlay.sidedEditor, request.contents))
          }
        },
        ModalityState.any(),
      )
    }
  }

  private fun createInlay(
    viewer: FrontendDiffViewer,
    sidedEditor: SidedEditor,
    contents: List<FrontendDiffContent>,
    disposable: Disposable,
  ): DebugInlay? {
    val renderer = FrontendDiffDebugInlayRenderer(buildDebugLines(viewer, sidedEditor, contents))
    val inlay = sidedEditor.editor.inlayModel.addBlockElement(
      0,
      false,
      true,
      0,
      renderer,
    ) ?: return null
    Disposer.register(disposable, inlay)
    return DebugInlay(sidedEditor, inlay, renderer)
  }

  @NonNls
  private fun buildDebugLines(
    viewer: FrontendDiffViewer,
    sidedEditor: SidedEditor,
    contents: List<FrontendDiffContent>,
  ): List<String> = buildList {
    add(
      "Frontend editor: side=${sidedEditor.side ?: "UNIFIED"}, document=${describeDocument(sidedEditor.editor.document)}, " +
      "viewerActual=${viewer.isActual}",
    )
    contents.forEachIndexed { contentIndex, content ->
      add(
        "FrontendDiffContent[$contentIndex]: file=${content.file?.url ?: "<none>"}, " +
        "document=${describeDocument(content.document)}, isCurrent=${content.isCurrent}, isEmpty=${content.isEmpty}",
      )
    }
  }

  /** The editors of [this] viewer, each with the diff side it shows, or `null` for a unified editor showing both. */
  private fun FrontendDiffViewer.sidedEditors(): List<SidedEditor> = when (this) {
    is FrontendDiffViewer.OneSide -> listOf(SidedEditor(side, editor))
    is FrontendDiffViewer.TwoSide -> listOf(SidedEditor(Side.LEFT, leftEditor), SidedEditor(Side.RIGHT, rightEditor))
    is FrontendDiffViewer.Unified -> listOf(SidedEditor(null, unifiedEditor))
  }

  @NonNls
  private fun describeDocument(document: Document?): String {
    return document?.let { "length=${it.textLength}, lines=${it.lineCount}, id=${System.identityHashCode(it)}" } ?: "<none>"
  }

  private data class SidedEditor(val side: Side?, val editor: Editor)

  private data class DebugInlay(
    val sidedEditor: SidedEditor,
    val inlay: Inlay<FrontendDiffDebugInlayRenderer>,
    val renderer: FrontendDiffDebugInlayRenderer,
  ) {
    @RequiresEdt
    fun update(lines: List<String>) {
      renderer.lines = lines
      if (inlay.isValid) inlay.update()
    }
  }

  private companion object {
    const val REGISTRY_KEY: String = "diff.frontend.debug.inlays"
  }
}

private class FrontendDiffDebugInlayRenderer(
  var lines: List<String>,
) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val editor = inlay.editor
    val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    val fontMetrics = editor.contentComponent.getFontMetrics(font)
    val textWidth = lines.maxOfOrNull(fontMetrics::stringWidth) ?: 0
    return maxOf(editor.contentComponent.width, textWidth + 2 * HORIZONTAL_PADDING)
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return lines.size * inlay.editor.lineHeight + 2 * VERTICAL_PADDING
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val editor = inlay.editor
    val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    val fontMetrics = editor.contentComponent.getFontMetrics(font)

    g.color = editor.colorsScheme.defaultBackground
    g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
    g.color = editor.colorsScheme.defaultForeground
    g.drawLine(targetRegion.x, targetRegion.y, targetRegion.x + targetRegion.width, targetRegion.y)
    g.font = font
    lines.forEachIndexed { index, line ->
      val baseline = targetRegion.y + VERTICAL_PADDING + fontMetrics.ascent + index * editor.lineHeight
      g.drawString(line, targetRegion.x + HORIZONTAL_PADDING, baseline)
    }
  }

  private companion object {
    val HORIZONTAL_PADDING: Int = JBUI.scale(8)
    val VERTICAL_PADDING: Int = JBUI.scale(4)
  }
}
