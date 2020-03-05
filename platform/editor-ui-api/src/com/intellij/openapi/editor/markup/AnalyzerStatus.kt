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

enum class InspectionsLevel(val type: String) {
  NONE("None"),
  ERRORS("Errors Only"),
  ALL("All Problems");

  override fun toString(): String = type
}

data class LanguageHighlightLevel(val language: Language, val level: InspectionsLevel)

data class StatInfo(val presentableName: String, val progress: Double, val finished: Boolean) {
  fun toPercent() : Int {
    val percent = (progress * 100).roundToInt()
    return if (percent == 100 && !finished) 99 else percent
  }
}

typealias AvailableLevels = List<InspectionsLevel>
typealias PassStat = List<StatInfo>

interface AnalyzerController {
  fun getActionMenu() : List<AnAction>
  fun getAvailableLevels() : AvailableLevels
  fun getHighlightLevels() : List<LanguageHighlightLevel>
  fun setHighLightLevel(newLevels: LanguageHighlightLevel)

  fun fillHectorPanels(container: Container, gc: GridBag)
  fun canClosePopup() : Boolean
  fun onClosePopup()
}

// Status
class AnalyzerStatus(val icon: Icon, title: String, details: String, val controller: () -> AnalyzerController) {
  val title = XmlStringUtil.wrapInHtml(title)
  val details = if (details.isNotEmpty()) XmlStringUtil.wrapInHtml(details) else ""
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

  companion object {
    @JvmStatic
    fun equals(a: AnalyzerStatus?, b: AnalyzerStatus?): Boolean {
      if (a == null && b == null) return true
      else if (a!= null && b != null) {
        return a.icon == b.icon &&
               a.expandedIcon == b.expandedIcon &&
               a.title == b.title && a.details == b.details &&
               a.showNavigation == b.showNavigation
      }
      else return false
    }
  }
}