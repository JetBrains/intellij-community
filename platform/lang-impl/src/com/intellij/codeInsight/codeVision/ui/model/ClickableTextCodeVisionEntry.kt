// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.codeVisionEntryMouseEventKey
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Same as [TextCodeVisionEntry], but with predefined click handler
 */
class ClickableTextCodeVisionEntry(text: String,
                                   providerId: String,
                                   val onClick: (MouseEvent?) -> Unit,
                                   icon: Icon? = null,
                                   longPresentation: String = text,
                                   tooltip: String = "",
                                   extraActions: List<CodeVisionEntryExtraActionModel> = listOf())
  : TextCodeVisionEntry(text, providerId, icon,
                        longPresentation, tooltip,
                        extraActions), CodeVisionPredefinedActionEntry {
  override fun onClick() {
    val mouseEvent = this.getUserData(codeVisionEntryMouseEventKey)
    onClick.invoke(mouseEvent)
  }
}