// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.codeVisionEntryMouseEventKey
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.openapi.editor.Editor
import java.awt.event.MouseEvent
import javax.swing.Icon

open class RichTextCodeVisionEntry(providerId: String,
                                   val text: RichText,
                                   icon: Icon? = null,
                                   longPresentation: String = "",
                                   tooltip: String = "",
                                   extraActions: List<CodeVisionEntryExtraActionModel> = emptyList())
  : CodeVisionEntry(providerId, icon, longPresentation, tooltip, extraActions)

class ClickableRichTextCodeVisionEntry(providerId: String,
                                       text: RichText,
                                       val onClick: (MouseEvent?, Editor) -> Unit,
                                       icon: Icon? = null,
                                       longPresentation: String = "",
                                       tooltip: String = "",
                                       extraActions: List<CodeVisionEntryExtraActionModel> = emptyList()) : RichTextCodeVisionEntry(
  providerId, text, icon, longPresentation, tooltip, extraActions), CodeVisionPredefinedActionEntry {
  override fun onClick(editor: Editor) {
    val mouseEvent = this.getUserData(codeVisionEntryMouseEventKey)
    onClick.invoke(mouseEvent, editor)
  }
}