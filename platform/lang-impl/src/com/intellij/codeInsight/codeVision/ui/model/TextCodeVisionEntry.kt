package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

open class TextCodeVisionEntry(@Nls val text: String,
                               providerId: String,
                               icon: Icon? = null,
                               @Nls longPresentation: String = text,
                               @NlsContexts.Tooltip tooltip: String = text,
                               extraActions: List<CodeVisionEntryExtraActionModel> = emptyList()) : CodeVisionEntry(providerId, icon, longPresentation,
                                                                                                                     tooltip,
                                                                                                                     extraActions)