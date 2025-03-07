package org.intellij.images.scientific.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.intellij.images.scientific.action.*

object ScientificImageActionsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("scientific.image.actions", 1)

  private val copyImageAction = GROUP.registerEvent("copy_image_action", EventFields.Class("action_handler"))
  private val saveImageAction = GROUP.registerEvent("save_image_action", EventFields.Class("action_handler"))
  private val restoreOriginalImageAction = GROUP.registerEvent("restore_original_image_action", EventFields.Class("action_handler"))
  private val invertChannelsAction = GROUP.registerEvent("invert_channels_action", EventFields.Class("action_handler"))
  private val grayscaleImageAction = GROUP.registerEvent("grayscale_image_action", EventFields.Class("action_handler"))
  private val binarizeImageAction = GROUP.registerEvent("binarize_image_action", EventFields.Class("action_handler"))

  override fun getGroup(): EventLogGroup = GROUP

  fun logCopyImageAction(action: CopyImageAction) {
    copyImageAction.log(action::class.java)
  }

  internal fun logSaveImageAction(action: SaveImageAction) {
    saveImageAction.log(action::class.java)
  }

  fun logRestoreOriginalImageAction(action: RestoreOriginalImageAction) {
    restoreOriginalImageAction.log(action::class.java)
  }

  fun logInvertChannelsAction(action: InvertChannelsAction) {
    invertChannelsAction.log(action::class.java)
  }

  fun logGrayscaleImageAction(action: GrayscaleImageAction) {
    grayscaleImageAction.log(action::class.java)
  }

  fun logBinarizeImageAction(action: BinarizeImageAction) {
    binarizeImageAction.log(action::class.java)
  }
}