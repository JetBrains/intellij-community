package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class AdditionalCodeVisionEntry(providerId: String,
                                @Nls val text: String,
                                @Nls longPresentation: String = "",
                                val swingIcon: Icon? = null) :
  CodeVisionEntry(providerId, null, longPresentation, longPresentation, emptyList())