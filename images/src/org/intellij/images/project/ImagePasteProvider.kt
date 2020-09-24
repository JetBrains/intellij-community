// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.project

import com.intellij.codeInsight.editorActions.PasteHandler
import com.intellij.ide.PasteProvider
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.imageFlavor
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.IOException
import javax.imageio.ImageIO


/**
 * Allows to paste screenshots as PNG files.
 *
 * NOTE: Text editors allows only [DataFlavor.stringFlavor] content to be pasted.
 * [ImagePasteProvider] is called when pasting to an editor (not to the project view) only if there is a [PasteHandler] that extracts data with [DataFlavor.imageFlavor].
 * @see [ImagePasteHandler.doExecute]
 */
class ImagePasteProvider : PasteProvider {
  override fun isPasteEnabled(dataContext: DataContext): Boolean =
    dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null
    && CopyPasteManager.getInstance().areDataFlavorsAvailable(imageFlavor)

  override fun isPastePossible(dataContext: DataContext): Boolean = true

  override fun performPaste(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    val currentFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val pasteContents = CopyPasteManager.getInstance().contents ?: return

    if (DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotification(
        LangBundle.message("popup.content.sorry.file.copy.paste.available.during.indexing"))
      return
    }

    val newFileParent = if (currentFile.isDirectory) currentFile else currentFile.parent
    if (newFileParent == null || !newFileParent.isDirectory) return

    val logger = logger<ImagePasteProvider>()

    runWriteAction {
      val nextAvailableName = VfsUtil.getNextAvailableName(newFileParent, "img", "png")

      // Step 1: Create file
      val imageFile = try {
        newFileParent.createChildData(this, nextAvailableName)
      }
                      catch (ioException: IOException) {
                        logger.error("Failed to create a pasted image file due to I/O error. Aborting operation.", ioException)
                        null
                      } ?: return@runWriteAction

      // Step 2: Obtain image data from the clipboard
      val multiResolutionImage =
        try {
          pasteContents.getTransferData(imageFlavor) as MultiResolutionImage
        }
        catch (ioException: IOException) {
          logger.error("Failed to get data from the clipboard. Data is no longer available. Aborting operation.", ioException)
          null
        }
        catch (classCastException: ClassCastException) {
          logger.error("Failed to get data from the clipboard. Data is not a multi-resolution image. Aborting operation.",
                       classCastException)
          null
        } ?: return@runWriteAction

      // Step 3: Save image data to the created file
      try {
        val first = multiResolutionImage.resolutionVariants.firstOrNull() ?: return@runWriteAction
        imageFile.getOutputStream(this)
          .use {
            ImageIO.write(first.toBufferedImage(), "png", it)
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

      dataContext.getData(CommonDataKeys.EDITOR)?.putUserData(PASTED_FILE_NAME, imageFile.name)
    }
  }

  companion object {
    @JvmStatic
    val PASTED_FILE_NAME = Key.create<String>("pasteFileName")
  }
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


