package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Creates lense with counter
 * Pattern [{count} {text}]
 */
class CounterCodeVisionEntry(val count: Int,
                             @Nls val text: String,
                             providerId: String,
                             icon: Icon?,
                             @Nls longPresentation: String,
                             @NlsContexts.Tooltip tooltip: String,
                             extraActions: List<CodeVisionEntryExtraActionModel>) : CodeVisionEntry(providerId, icon, longPresentation,
                                                                                                    tooltip,
                                                                                                    extraActions)