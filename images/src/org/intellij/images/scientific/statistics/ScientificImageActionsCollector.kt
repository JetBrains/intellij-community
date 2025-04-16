package org.intellij.images.scientific.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.intellij.images.scientific.action.*

object ScientificImageActionsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("scientific.image.actions", 2)

  private val ACTION_HANDLER_FIELD = EventFields.Class("action_handler")
  private val IMAGE_FORMAT_FIELD = EventFields.String("image_format", listOf("png", "jpg", "jpeg", "bmp"))

  private val INVOKED_COPY_IMAGE_EVENT = GROUP.registerEvent("copy_image_action", ACTION_HANDLER_FIELD)
  private val INVOKED_SAVE_IMAGE_EVENT = GROUP.registerEvent("save_image_action", ACTION_HANDLER_FIELD, IMAGE_FORMAT_FIELD)
  private val INVOKED_RESTORE_ORIGINAL_IMAGE_EVENT = GROUP.registerEvent("restore_original_image_action", ACTION_HANDLER_FIELD)
  private val INVOKED_INVERT_IMAGE_CHANNELS_EVENT = GROUP.registerEvent("invert_channels_action", ACTION_HANDLER_FIELD)
  private val INVOKED_GRAYSCALE_IMAGE_EVENT = GROUP.registerEvent("grayscale_image_action", ACTION_HANDLER_FIELD)
  private val INVOKED_BINARY_IMAGE_EVENT = GROUP.registerEvent("binarize_image_action", ACTION_HANDLER_FIELD)
  private val INVOKED_ROTATE_IMAGE_EVENT = GROUP.registerEvent("rotate_image_action", ACTION_HANDLER_FIELD)

  override fun getGroup(): EventLogGroup = GROUP

  fun logCopyImageInvoked(action: CopyImageAction) {
    INVOKED_COPY_IMAGE_EVENT.log(action::class.java)
  }

  internal fun logSaveAsImageInvoked(action: SaveImageAction, imageFormat: String) {
    INVOKED_SAVE_IMAGE_EVENT.log(action::class.java, imageFormat)
  }

  fun logRestoreOriginalImageInvoked(action: RestoreOriginalImageAction) {
    INVOKED_RESTORE_ORIGINAL_IMAGE_EVENT.log(action::class.java)
  }

  fun logInvertChannelsInvoked(action: InvertChannelsAction) {
    INVOKED_INVERT_IMAGE_CHANNELS_EVENT.log(action::class.java)
  }

  fun logGrayscaleImageInvoked(action: GrayscaleImageAction) {
    INVOKED_GRAYSCALE_IMAGE_EVENT.log(action::class.java)
  }

  fun logBinarizeImageInvoked(action: BinarizeImageAction) {
    INVOKED_BINARY_IMAGE_EVENT.log(action::class.java)
  }

  fun logRotateImageInvoked(action: RotateImageAction) {
    INVOKED_ROTATE_IMAGE_EVENT.log(action::class.java)
  }
}