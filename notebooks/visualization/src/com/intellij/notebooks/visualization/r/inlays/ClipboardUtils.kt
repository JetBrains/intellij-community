/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.notebooks.visualization.r.inlays

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.system.OS
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// TODO: Some problems with getting "event.isControlDown" on component placed on top of Idea Editor content.
/** Clipboard utils to realize Ctrl+C functionality in Table and Plots. */
object ClipboardUtils {

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

  // Standard copy works incorrectly on Mac, see JBR-6788,
  // But it seemingly was fixed in the newer (15 and later) Mac versions
  fun isIntermediateFileWorkaroundNeeded(): Boolean = OS.CURRENT == OS.macOS && !OS.CURRENT.isAtLeast(15, 0)

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