package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.images.editor.ImageDocument.IMAGE_DOCUMENT_DATA_KEY
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.utils.ScientificUtils.NORMALIZATION_APPLIED_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ORIGINAL_IMAGE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.ROTATION_ANGLE_KEY
import org.intellij.images.scientific.utils.ScientificUtils.saveImageToFile
import org.intellij.images.scientific.utils.launchBackground
import java.awt.image.BufferedImage

class DisplaySingleChannelAction(
  private val channelIndex: Int,
  text: String
) : DumbAwareAction(text) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val originalImage = imageFile?.getUserData(ORIGINAL_IMAGE_KEY)
    e.presentation.isEnabled = originalImage != null && getDisplayableChannels(originalImage) > 1
  }


  private fun getDisplayableChannels(image: BufferedImage): Int {
    val totalChannels = image.raster.numBands
    return if (image.colorModel.hasAlpha()) totalChannels - 1 else totalChannels
  }

  override fun actionPerformed(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val originalImage = imageFile.getUserData(ORIGINAL_IMAGE_KEY) ?: return
    val document = e.getData(IMAGE_DOCUMENT_DATA_KEY) ?: return
    val currentAngle = imageFile.getUserData(ROTATION_ANGLE_KEY) ?: 0
    imageFile.putUserData(NORMALIZATION_APPLIED_KEY, false)

    launchBackground {
      val rotatedOriginal = if (currentAngle != 0) ScientificUtils.rotateImage(originalImage, currentAngle) else originalImage
      val channelImage = displaySingleChannel(rotatedOriginal, channelIndex)
      saveImageToFile(imageFile, channelImage)
      document.value = channelImage
      ScientificImageActionsCollector.logChannelSelection(channelIndex)
    }
  }

  private suspend fun displaySingleChannel(image: BufferedImage, channelIndex: Int): BufferedImage = withContext(Dispatchers.IO) {
    val raster = image.raster
    val width = image.width
    val height = image.height

    val channelImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

    for (x in 0 until width) {
      for (y in 0 until height) {
        val value = raster.getSample(x, y, channelIndex)
        channelImage.raster.setSample(x, y, 0, value)
      }
    }
    channelImage
  }
}