package com.intellij.codeInsight.codeVision

import javax.swing.Icon

/**
 * @property providerId The provider ID of this entry
 * @property icon Icon to show near this entry in editor
 * @property longPresentation The text in 'More' menu
 * @property tooltip Tooltip text
 * @property extraActions Extra actions for this lens
 */
abstract class CodeVisionEntry(val providerId: String,
                               val icon: Icon?,
                               val longPresentation: String,
                               val tooltip: String,
                               val extraActions: List<CodeVisionEntryExtraActionModel>)