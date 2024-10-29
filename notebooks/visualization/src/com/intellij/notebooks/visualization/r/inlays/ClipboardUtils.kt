/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.notebooks.visualization.r.inlays

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.notebooks.visualization.r.VisualizationBundle
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JTable

// TODO: Some problems with getting "event.isControlDown" on component placed on top of Idea Editor content.
/** Clipboard utils to realize Ctrl+C functionality in Table and Plots. */
object ClipboardUtils {

  const val LINE_BREAK = "\r"
  private const val CELL_BREAK = "\t"

  private fun copyAllToString(table: JTable, cellBreak: String = CELL_BREAK, limit: Int = Int.MAX_VALUE): String {
    if (table.rowCount == 0 || table.columnCount == 0) {
      // The code should be compatible with 193 and 201 so, so we cannot use NotificationGroup.createIdWithTitle yet
      Notifications.Bus.notify(Notification("Notebook Table",
                                            VisualizationBundle.message("clipboard.utils.error"),
                                            VisualizationBundle.message("clipboard.utils.no.columns.or.rows"),
                                            NotificationType.ERROR))
      return ""
    }

    val builder = StringBuilder()
    for (i in 0 until table.rowCount) {
      if (i >= limit) {
        builder.append("\n").append(VisualizationBundle.message("clipboard.utils.copy.load.limit", limit))
        break
      }
      for (j in 0 until table.columnCount) {
        table.getValueAt(i, j)?.let { builder.append(escape(it, cellBreak)) }

        if (j < table.columnCount - 1) {
          builder.append(cellBreak)
        }
      }
      if (i != table.rowCount - 1) {
        builder.append(LINE_BREAK)
      }
    }

    return builder.toString()
  }

  fun copyAllToClipboard(table: JTable, cellBreak: String = CELL_BREAK, limit: Int = Int.MAX_VALUE) {
    val sel = StringSelection(copyAllToString(table, cellBreak, limit))
    CopyPasteManager.getInstance().setContents(sel)
  }

  fun copySelectedToString(table: JTable, cellBreak: String = CELL_BREAK): String {

    val selectedColumnCount = table.selectedColumnCount
    val selectedRowCount = table.selectedRowCount
    val selectedRows = table.selectedRows
    val selectedColumns = table.selectedColumns

    if (selectedColumnCount == 0 || selectedRowCount == 0) {
      Notifications.Bus.notify(Notification("Notebook Table",
                                            VisualizationBundle.message("clipboard.utils.error"),
                                            VisualizationBundle.message("clipboard.utils.no.selection"),
                                            NotificationType.ERROR))
      return ""
    }

    val builder = StringBuilder()
    for (i in 0 until selectedRowCount) {
      for (j in 0 until selectedColumnCount) {
        table.getValueAt(selectedRows[i], selectedColumns[j])?.let {
          val cellString = if (selectedColumnCount > 1) escape(it, cellBreak) else it.toString()
          builder.append(cellString)
        }

        if (j < selectedColumnCount - 1) {
          builder.append(cellBreak)
        }
      }
      if (i != selectedRowCount - 1) {
        builder.append(LINE_BREAK)
      }
    }
    return builder.toString()
  }

  fun copySelectedToClipboard(table: JTable, cellBreak: String = CELL_BREAK) {
    val sel = StringSelection(copySelectedToString(table, cellBreak))
    CopyPasteManager.getInstance().setContents(sel)
  }

  fun escape(cell: Any, cellBreak: String = CELL_BREAK): String {
    val cellString = cell.toString()
    return if (cellString.contains(LINE_BREAK) || cellString.contains(cellBreak)) {
      "\"${StringUtil.escapeQuotes(cellString)}\""
    }
    else {
      cellString
    }
  }

  fun copyImageToClipboard(image: Image) {
    CopyPasteManager.getInstance().setContents(ImageTransferable(image))
  }

  // We have a situation that on some MacOS the image is copied as TIFF.
  // To avoid this situation on Mac, we have a special transferable which copies image as png.
  // https://bugs.openjdk.org/browse/JDK-8313706
  // https://youtrack.jetbrains.com/issue/PY-69919/Result-of-Copy-plot-command-results-in-Unsupported-image-type-in-Google-Slides
  fun copyPngImageToClipboard(image: BufferedImage) {
    val tempImage = FileUtil.createTempFile("clipboard", ".png")
    ImageIO.write(image, "png", tempImage)

    CopyPasteManager.getInstance().setContents(FileTransferable(listOf(tempImage)))
  }

  private class FileTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.javaFileListFlavor.equals(flavor)
    override fun getTransferData(flavor: DataFlavor): List<File> {
      if (!isDataFlavorSupported(flavor))
        throw UnsupportedFlavorException(flavor)

      return files
    }
  }

  private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
      return arrayOf(DataFlavor.imageFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
      return flavor == DataFlavor.imageFlavor
    }

    override fun getTransferData(flavor: DataFlavor): Any {
      if (!isDataFlavorSupported(flavor)) {
        throw UnsupportedFlavorException(flavor)
      }
      return image
    }
  }
}