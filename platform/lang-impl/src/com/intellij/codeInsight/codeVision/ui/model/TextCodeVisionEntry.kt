package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import javax.swing.Icon

open class TextCodeVisionEntry(val text: String,
                               providerId: String,
                               icon: Icon? = null,
                               longPresentation: String = text,
                               tooltip: String = text,
                               extraActions: List<CodeVisionEntryExtraActionModel> = emptyList()) : CodeVisionEntry(providerId, icon, longPresentation,
                                                                                                                     tooltip,
                                                                                                                     extraActions)