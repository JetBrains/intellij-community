package com.intellij.codeInsight.codeVision

import com.intellij.openapi.util.UserDataHolderBase
import javax.swing.Icon

/**
 * @property providerId The provider ID of this entry
 * @property icon Icon to show near this entry in editor
 * @property longPresentation The text in 'More' menu
 * @property tooltip Tooltip text
 * @property extraActions Extra actions for this lens
 * extra actions available with right click on inlay
 */
abstract class CodeVisionEntry(val providerId: String,
                               val icon: Icon?,
                               val longPresentation: String,
                               val tooltip: String,
                               val extraActions: List<CodeVisionEntryExtraActionModel>) : UserDataHolderBase() {
  /**
   * Defines if we show entry in 'More' popup
   */
  var showInMorePopup: Boolean = true
}