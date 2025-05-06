package org.intellij.images.scientific.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object ScientificImageActionsCollector : CounterUsagesCollector() {
  @JvmStatic
  private val GROUP = EventLogGroup("scientific.image.actions", 5)
  override fun getGroup(): EventLogGroup = GROUP

  private val imageFormatField = EventFields.String("image_format", listOf("png", "jpg", "jpeg", "bmp", "svg"))
  private val channelIndexField = EventFields.Int("channel_index")
  private val rotateAngleField = EventFields.Int("rotate_angle")
  private val isNormalizeField = EventFields.Boolean("is_normalize")
  private val binarizationThresholdField = EventFields.Int("binarization_threshold")

  private val invokedCopyImageEvent = GROUP.registerEvent("debug.image.view.copy")
  private val invokedSaveImageEvent = GROUP.registerEvent("debug.image.view.save", imageFormatField)
  private val invokedRestoreOriginalImageEvent = GROUP.registerEvent("debug.image.view.restore.original")
  private val invokedInvertChannelsEvent = GROUP.registerEvent("debug.image.view.invert.channels")
  private val invokedGrayscaleImageEvent = GROUP.registerEvent("debug.image.view.grayscale")
  private val invokedBinaryImageEvent = GROUP.registerEvent("debug.image.view.binarize", binarizationThresholdField)
  private val invokedRotateImageEvent = GROUP.registerEvent("debug.image.view.rotate", rotateAngleField)
  private val invokedChannelSelectionEvent = GROUP.registerEvent("debug.image.view.channel.selection", channelIndexField)
  private val invokedNormalizeImageEvent = GROUP.registerEvent("debug.image.view.normalize", isNormalizeField)

  fun logCopyImageInvoked() {
    invokedCopyImageEvent.log()
  }

  internal fun logSaveAsImageInvoked(imageFormat: String) {
    invokedSaveImageEvent.log(imageFormat)
  }

  fun logRestoreOriginalImageInvoked() {
    invokedRestoreOriginalImageEvent.log()
  }

  fun logInvertChannelsInvoked() {
    invokedInvertChannelsEvent.log()
  }

  fun logGrayscaleImageInvoked() {
    invokedGrayscaleImageEvent.log()
  }

  fun logBinarizeImageInvoked(binarizationThreshold: Int) {
    invokedBinaryImageEvent.log(binarizationThreshold)
  }

  fun logRotateImageInvoked(rotationAngle: Int) {
    invokedRotateImageEvent.log(rotationAngle)
  }

  fun logChannelSelection(channelIndex: Int) {
    invokedChannelSelectionEvent.log(channelIndex)
  }

  fun logNormalizeImageInvoked(isNormalize: Boolean) {
    invokedNormalizeImageEvent.log(isNormalize)
  }
}