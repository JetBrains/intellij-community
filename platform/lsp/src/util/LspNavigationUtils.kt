// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.util

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.LspClient
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import org.eclipse.lsp4j.Location
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.JList

/**
 * Navigates to a single LSP location or shows a popup if there are multiple locations.
 *
 * @param client The LSP server instance
 * @param locations The list of locations to navigate to
 * @param title The title for the popup if multiple locations exist (e.g., "References", "Implementations")
 * @param mouseEvent Mouse event for popup positioning
 */
fun navigateOrShowPopup(client: LspClient, locations: List<Location>, @Nls title: String, mouseEvent: MouseEvent?) {
  when {
    locations.isEmpty() -> return
    locations.size == 1 -> navigateToLocation(client, locations.first())
    else -> showLspNavigationPopup(client, locations, title, mouseEvent)
  }
}

fun navigateToLocation(client: LspClient, location: Location) {
  val file = client.descriptor.findFileByUri(location.uri) ?: return
  val descriptor = OpenFileDescriptor(client.project, file, location.range.start.line, location.range.start.character)
  FileEditorManager.getInstance(client.project).openTextEditor(descriptor, true)
}

/**
 * Creates and shows a popup for LSP locations (references, implementations, etc.).
 * Automatically resolves file content and handles navigation.
 *
 * Consider using [navigateOrShowPopup] instead if you want to navigate directly when there's only one location.
 */
fun showLspNavigationPopup(client: LspClient, locations: List<Location>, @Nls title: String, mouseEvent: MouseEvent?) {
  if (locations.isEmpty()) return

  val project = client.project
  val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

  JBPopupFactory.getInstance().createPopupChooserBuilder(locations)
    .setTitle(title)
    .setRenderer(object : ColoredListCellRenderer<Location>() {
      @Suppress("HardCodedStringLiteral")
      override fun customizeCellRenderer(
        list: JList<out Location>,
        value: Location,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
      ) {
        val file = client.descriptor.findFileByUri(value.uri)
        if (file == null) {
          append(value.uri, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          return
        }
        icon = file.fileType.icon

        val document = runReadActionBlocking {
          FileDocumentManager.getInstance().getDocument(file)
        }
        val range = value.range

        // Handle case where file exists but content/range is invalid
        if (document == null || range.start.line < 0 || range.start.line >= document.lineCount) {
          append(" ${file.name}:${range.start.line + 1}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          return
        }

        val lineStart = document.getLineStartOffset(range.start.line)
        val lineEnd = document.getLineEndOffset(range.start.line)
        val lineText = document.getText(TextRange(lineStart, lineEnd)).trim()

        val matchStart = range.start.character.coerceIn(0, lineText.length)
        val matchEnd = range.end.character.coerceIn(matchStart, lineText.length)

        val isSingleLine = range.start.line == range.end.line
        val isPartialLine = isSingleLine && (matchStart > 0 || matchEnd < lineText.length)
        val shouldHighlight = isPartialLine && matchStart < matchEnd

        if (shouldHighlight) {
          append(lineText.substring(0, matchStart))
          append(lineText.substring(matchStart, matchEnd), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append(lineText.substring(matchEnd))
        }
        else {
          append(lineText)
          if (!isSingleLine) {
            val additionalLines = range.end.line - range.start.line
            append(" +$additionalLines ${if (additionalLines == 1) "line" else "lines"}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }

        append(" ${file.name}:${range.start.line + 1}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    })
    .setItemChosenCallback { location: Location -> navigateToLocation(client, location) }
    .createPopup()
    .apply {
      if (mouseEvent != null) {
        show(RelativePoint(mouseEvent))
      }
      else {
        showInBestPositionFor(editor)
      }
    }
}