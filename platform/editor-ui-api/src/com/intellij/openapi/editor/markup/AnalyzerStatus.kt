// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.util.ui.GridBag
import com.intellij.xml.util.XmlStringUtil
import java.awt.Container
import java.util.*
import javax.swing.Icon
import kotlin.math.roundToInt

// Analyzer Highlight Level
enum class AHLevel(val type: String) {
  NONE("None"),
  ERRORS("Errors Only"),
  ALL("All Problems");

  override fun toString(): String = type
}

data class LanguageHighlightLevel(val language: Language, val level: AHLevel)

data class StatInfo(val presentableName: String, val progress: Double, val finished: Boolean) {
  fun toPercent() : Int {
    val percent = (progress * 100).roundToInt()
    return if (percent == 100 && !finished) 99 else percent
  }
}

// Result in a callable controller
typealias HighlightLevels = List<LanguageHighlightLevel>
typealias AvailableLevels = List<AHLevel>
typealias PassStat = List<StatInfo>

interface AnalyzerController {
  fun getActionMenu() : List<AnAction>
  fun getAvailableLevels() : AvailableLevels
  fun getHighlightLevels() : HighlightLevels
  fun setHighLightLevel(newLevels: LanguageHighlightLevel)

  fun fillHectorPanels(container: Container, gc: GridBag)
  fun onClose() : Boolean
}

// Status
class AnalyzerStatus(val icon: Icon, title: String, details: String?, val controller: () -> AnalyzerController) {
  val title = XmlStringUtil.wrapInHtml(title)
  val details = if (details != null) XmlStringUtil.wrapInHtml(details) else ""
  var showNavigation = false
  var expandedIcon: Icon = icon
  var passStat : PassStat = Collections.emptyList()

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }

  fun withExpandedIcon(eIcon: Icon): AnalyzerStatus {
    expandedIcon = eIcon
    return this
  }

  fun withPathStat(pStat: PassStat) : AnalyzerStatus {
    passStat = pStat
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (other !is AnalyzerStatus) return false

    return icon == other.icon &&
           expandedIcon == other.expandedIcon &&
           title == other.title && details == other.details &&
           showNavigation == other.showNavigation
  }
}