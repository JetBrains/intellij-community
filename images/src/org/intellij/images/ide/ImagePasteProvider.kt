// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.ide

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.MultiResolutionImageProvider
import org.intellij.images.ImagesBundle
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Represents a basic paste provider that allows to paste screenshots (from clipboard) as PNG files.
 *
 * NOTE: If registered as `filePasteProvider` handles paste operations in project view.
 */
open class ImagePasteProvider : PasteProvider {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  final override fun isPastePossible(dataContext: DataContext): Boolean = true
  final override fun isPasteEnabled(dataContext: DataContext): Boolean =
    CopyPasteManager.getInstance().areDataFlavorsAvailable(imageFlavor)
    && dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null
    && isEnabledForDataContext(dataContext)

  open fun isEnabledForDataContext(dataContext: DataContext): Boolean = true

  final override fun performPaste(dataContext: DataContext) {
    val currentFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    if (SystemInfoRt.isMac) {
      (SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap)?.addFlavorForUnencodedNative("PNG", PNG_FLAVOR)
    }
    val pasteContents = CopyPasteManager.getInstance().contents ?: return

    val newFileParent = if (currentFile.isDirectory) currentFile else currentFile.parent
    if (newFileParent == null || !newFileParent.isDirectory) return

    val logger = logger<ImagePasteProvider>()

    // Step 1: Obtain image data from the clipboard
    val imageToPaste = try {
      pasteContents.pngBytes() ?: pasteContents.getTransferData(imageFlavor).toPngBytes()
    }
    catch (ioException: IOException) {
      logger.error("Failed to get data from the clipboard. Data is no longer available. Aborting operation.", ioException)
      return
    }

    if (imageToPaste == null) {
      logger.error("Failed to get data from the clipboard. Nothing to paste. Aborting operation.")
      return
    }

    val fileName = VfsUtil.getNextAvailableName(newFileParent, "img", "png")
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    CommandProcessor.getInstance().executeCommand(project, {
      runWriteAction {

        // Step 2: Create file
        val imageFile = try {
          newFileParent.createChildData(this, fileName)
        }
                        catch (ioException: IOException) {
                          logger.error("Failed to create a pasted image file due to I/O error. Aborting operation.", ioException)
                          null
                        } ?: return@runWriteAction

        // Step 3: Save image data to the created file
        try {
          imageFile.getOutputStream(this)
            .use {
              it.write(imageToPaste)
            }
        }
        catch (ioException: IOException) {
          logger.error("Failed to save a pasted image to a file due to I/O error. Aborting operation", ioException)

          // cleaning empty file
          try {
            imageFile.delete(this)
          }
          catch (_: IOException) {
            // just skip it
          }

          return@runWriteAction
        }

        imageFilePasted(dataContext, imageFile)
      }
    }, ImagesBundle.message("paste.image.command.name", fileName), null, UndoConfirmationPolicy.REQUEST_CONFIRMATION)
  }

  open fun imageFilePasted(dataContext: DataContext, imageFile: VirtualFile) = Unit
}

private val PNG_FLAVOR = DataFlavor("image/png;class=java.io.InputStream", "PNG")

private fun Transferable.pngBytes(): ByteArray? {
  if (!isDataFlavorSupported(PNG_FLAVOR)) return null
  return (getTransferData(PNG_FLAVOR) as InputStream).use { it.readAllBytes() }
}

private fun Any?.toPngBytes(): ByteArray? {
  if (this !is Image) return null
  val output = ByteArrayOutputStream()
  ImageIO.write(MultiResolutionImageProvider.getMaxSizeResolutionVariant(this).toBufferedImage(), "png", output)
  return output.toByteArray()
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
