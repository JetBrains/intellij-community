// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.ide

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Image
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.IOException
import javax.imageio.ImageIO


/**
 * Represents a basic paste provider that allows to paste screenshots (from clipboard) as PNG files.
 *
 * NOTE: If registered as `filePasteProvider` handles paste operations in project view.
 */
open class ImagePasteProvider : PasteProvider {
  final override fun isPastePossible(dataContext: DataContext): Boolean = true
  final override fun isPasteEnabled(dataContext: DataContext): Boolean =
    CopyPasteManager.getInstance().areDataFlavorsAvailable(imageFlavor)
    && dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null
    && isEnabledForDataContext(dataContext)

  open fun isEnabledForDataContext(dataContext: DataContext): Boolean = true

  final override fun performPaste(dataContext: DataContext) {
    val currentFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val pasteContents = CopyPasteManager.getInstance().contents ?: return

    val newFileParent = if (currentFile.isDirectory) currentFile else currentFile.parent
    if (newFileParent == null || !newFileParent.isDirectory) return

    val logger = logger<ImagePasteProvider>()

    // Step 1: Obtain image data from the clipboard
    val imageToPaste = try {
      pasteContents.getTransferData(imageFlavor)
    }
    catch (ioException: IOException) {
      logger.error("Failed to get data from the clipboard. Data is no longer available. Aborting operation.", ioException)
      return
    }.let {
      when (it) {
        is MultiResolutionImage -> it.resolutionVariants.firstOrNull()?.toBufferedImage()
        is BufferedImage -> it
        is Image -> it.toBufferedImage()
        else -> null
      }
    }

    if (imageToPaste == null) {
      logger.error("Failed to get data from the clipboard. Nothing to paste. Aborting operation.")
      return
    }

    runWriteAction {
      val nextAvailableName = VfsUtil.getNextAvailableName(newFileParent, "img", "png")

      // Step 2: Create file
      val imageFile = try {
        newFileParent.createChildData(this, nextAvailableName)
      }
                      catch (ioException: IOException) {
                        logger.error("Failed to create a pasted image file due to I/O error. Aborting operation.", ioException)
                        null
                      } ?: return@runWriteAction

      // Step 3: Save image data to the created file
      try {
        imageFile.getOutputStream(this)
          .use {
            ImageIO.write(imageToPaste, "png", it)
          }
      }
      catch (ioException: IOException) {
        logger.error("Failed to save a pasted image to a file due to I/O error. Aborting operation", ioException)

        // cleaning empty file
        try {
          imageFile.delete(this)
        }
        catch (ioException: IOException) {
          // just skip it
        }

        return@runWriteAction
      }

      imageFilePasted(dataContext, imageFile)
    }
  }

  open fun imageFilePasted(dataContext: DataContext, imageFile: VirtualFile) = Unit
}

private fun Image.toBufferedImage() = let { img ->
  when (img) {
    is BufferedImage -> img
    else -> {
      // Create a buffered image with transparency
      val bufferedImage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)

      // Draw the image on to the buffered image
      val bGr = bufferedImage.createGraphics()
      bGr.drawImage(img, 0, 0, null)
      bGr.dispose()

      bufferedImage
    }
  }
}
