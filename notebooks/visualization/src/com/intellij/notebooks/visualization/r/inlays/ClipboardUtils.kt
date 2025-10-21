// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.notebooks.visualization.r.inlays

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

// TODO: Some problems with getting "event.isControlDown" on component placed on top of Idea Editor content.
/** Clipboard utils to realize Ctrl+C functionality in Table and Plots. */
object ClipboardUtils {

  fun copyImageToClipboard(image: Image) {
    CopyPasteManager.getInstance().setContents(ImageTransferable(image))
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