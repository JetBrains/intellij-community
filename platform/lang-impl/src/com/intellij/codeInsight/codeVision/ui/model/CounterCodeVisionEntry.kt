package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import javax.swing.Icon

/**
 * Creates lense with counter
 * Pattern [{count} {text}]
 */
class CounterCodeVisionEntry(val count: Int,
                             val text: String,
                             providerId: String,
                             icon: Icon?,
                             longPresentation: String,
                             tooltip: String,
                             extraActions: List<CodeVisionEntryExtraActionModel>) : CodeVisionEntry(providerId, icon, longPresentation,
                                                                                                    tooltip,
                                                                                                    extraActions)