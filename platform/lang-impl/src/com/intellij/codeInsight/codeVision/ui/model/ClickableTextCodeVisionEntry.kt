// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.codeVisionEntryMouseEventKey
import com.intellij.openapi.editor.Editor
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Same as [TextCodeVisionEntry], but with predefined click handler
 * @param onClick click handler. MouseEvent can be null when click passed from ListPopup and click position shouldn't be important.
 *
 * WARNING: do not store PSI inside handler. Use classes to avoid accidental psi capture.
 */
class ClickableTextCodeVisionEntry(text: String,
                                   providerId: String,
                                   val onClick: (MouseEvent?, Editor) -> Unit,
                                   icon: Icon? = null,
                                   longPresentation: String = text,
                                   tooltip: String = "",
                                   extraActions: List<CodeVisionEntryExtraActionModel> = listOf())
  : TextCodeVisionEntry(text, providerId, icon,
                        longPresentation, tooltip,
                        extraActions), CodeVisionPredefinedActionEntry {
  override fun onClick(editor: Editor) {
    val mouseEvent = this.getUserData(codeVisionEntryMouseEventKey)
    onClick.invoke(mouseEvent, editor)
  }
}