package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

class AdditionalCodeVisionEntry(providerId: String,
                                val text: String,
                                @NlsContexts.Tooltip longPresentation: String = "",
                                val swingIcon: Icon? = null) :
  CodeVisionEntry(providerId, null, longPresentation, longPresentation, emptyList())