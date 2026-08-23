// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.frontend.FrontendDiffContent
import com.intellij.diff.frontend.FrontendDiffContext
import com.intellij.diff.frontend.FrontendDiffEditor
import com.intellij.diff.frontend.FrontendDiffExtension
import com.intellij.diff.frontend.FrontendDiffLineMapper
import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
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
  override fun onViewerCreated(viewer: FrontendDiffViewer, context: FrontendDiffContext) {
    if (!RegistryManager.getInstance().`is`(REGISTRY_KEY)) return

    val mapping = (viewer as? FrontendDiffViewer.FrontendUnifiedDiffViewer)?.mapping
    val inlays = viewer.editors.mapIndexedNotNull { index, frontendEditor ->
      createInlay(viewer, frontendEditor, index, context.request.contents, mapping, context.parentDisposable)
    }

    val scheduleUpdate = {
      ApplicationManager.getApplication().invokeLater(
        {
          for (debugInlay in inlays) {
            if (!debugInlay.inlay.isValid) continue
            debugInlay.update(buildDebugLines(viewer, debugInlay.frontendEditor, debugInlay.index, context.request.contents, mapping))
          }
        },
        ModalityState.any(),
      )
    }
    mapping?.addListener(context.parentDisposable, scheduleUpdate)
    for (frontendEditor in viewer.editors) {
      viewer.lineMapper(frontendEditor)?.addListener(context.parentDisposable, scheduleUpdate)
    }
  }

  private fun createInlay(
    viewer: FrontendDiffViewer,
    frontendEditor: FrontendDiffEditor,
    index: Int,
    contents: List<FrontendDiffContent>,
    mapping: FrontendUnifiedDiffMapping?,
    parentDisposable: Disposable,
  ): DebugInlay? {
    val renderer = FrontendDiffDebugInlayRenderer(buildDebugLines(viewer, frontendEditor, index, contents, mapping))
    val inlay = frontendEditor.editor.inlayModel.addBlockElement(
      0,
      false,
      true,
      0,
      renderer,
    ) ?: return null
    Disposer.register(parentDisposable, inlay)
    return DebugInlay(frontendEditor, index, inlay, renderer)
  }

  @NonNls
  private fun buildDebugLines(
    viewer: FrontendDiffViewer,
    frontendEditor: FrontendDiffEditor,
    index: Int,
    contents: List<FrontendDiffContent>,
    mapping: FrontendUnifiedDiffMapping?,
  ): List<String> = buildList {
    val lineMapper = viewer.lineMapper(frontendEditor)
    add(
      "Frontend editor $index: side=${viewer.side(frontendEditor) ?: "UNIFIED"}, document=${describeDocument(frontendEditor.editor.document)}, " +
      "mapperAvailable=${lineMapper?.isAvailable ?: false}",
    )
    contents.forEachIndexed { contentIndex, content ->
      add(
        "FrontendDiffContent[$contentIndex]: file=${content.file?.url ?: "<none>"}, " +
        "document=${describeDocument(content.document)}, isCurrent=${content.isCurrent}, isEmpty=${content.isEmpty}",
      )
    }
    if (mapping != null) {
      add("FrontendUnifiedDiffMapping: available=${mapping.isAvailable}, revision=${mapping.revision}")
    }
  }

  private fun FrontendDiffViewer.side(frontendEditor: FrontendDiffEditor): Side? = when (this) {
    is FrontendDiffViewer.FrontendOneSideDiffViewer -> side.takeIf { editor == frontendEditor }
    is FrontendDiffViewer.FrontendTwoSideDiffViewer -> when (frontendEditor) {
      left -> Side.LEFT
      right -> Side.RIGHT
      else -> null
    }
    is FrontendDiffViewer.FrontendUnifiedDiffViewer -> null
  }

  private fun FrontendDiffViewer.lineMapper(frontendEditor: FrontendDiffEditor): FrontendDiffLineMapper? = when (this) {
    is FrontendDiffViewer.FrontendOneSideDiffViewer -> lineMapper.takeIf { editor == frontendEditor }
    is FrontendDiffViewer.FrontendTwoSideDiffViewer -> when (frontendEditor) {
      left -> leftLineMapper
      right -> rightLineMapper
      else -> null
    }
    is FrontendDiffViewer.FrontendUnifiedDiffViewer -> lineMapper.takeIf { editor == frontendEditor }
  }

  @NonNls
  private fun describeDocument(document: Document?): String {
    return document?.let { "length=${it.textLength}, lines=${it.lineCount}, id=${System.identityHashCode(it)}" } ?: "<none>"
  }

  private data class DebugInlay(
    val frontendEditor: FrontendDiffEditor,
    val index: Int,
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
