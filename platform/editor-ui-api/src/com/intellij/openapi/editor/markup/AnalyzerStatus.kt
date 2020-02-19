// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.xml.util.XmlStringUtil
import javax.swing.Icon

// Analyzer Highlight Level
enum class AHLevel(val type: String) {
  NONE("None"),
  ERRORS("Errors Only"),
  ALL("All Problems");

  override fun toString(): String = type
}

data class LanguageHighlightLevel(val language: Language, val level: AHLevel)

// Result in a callable controller
typealias HighlightLevels = List<LanguageHighlightLevel>
typealias AvailableLevels = List<AHLevel>

interface AnalyzerController {
  fun getActionMenu() : AnAction
  fun getAvailableLevels() : AvailableLevels
  fun getHighlightLevels() : HighlightLevels
  fun setHighLightLevel(newLevels: LanguageHighlightLevel)
}

// Status
class AnalyzerStatus(val icon: Icon?, statusText: String, val controller: () -> AnalyzerController) {
  val statusText = XmlStringUtil.wrapInHtml(statusText)
  var showNavigation = false

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }
}