// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorBundle
import com.intellij.util.ui.GridBag
import com.intellij.xml.util.XmlStringUtil
import java.awt.Container
import java.util.*
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * Inspection highlight level with string representations bound to resources for i18n.
 */
enum class InspectionsLevel(private val bundleKey: String) {
  NONE("iw.level.none"),
  ERRORS("iw.level.errors"),
  ALL("iw.level.all");

  override fun toString(): String = EditorBundle.message(bundleKey)
}

/*
 * Per language highlight level
 */
data class LanguageHighlightLevel(val language: Language, val level: InspectionsLevel)

/**
 * Light wrapper for <code>ProgressableTextEditorHighlightingPass</code> with only essential UI data.
 */
data class PassWrapper(val presentableName: String, val progress: Double, val finished: Boolean) {
  fun toPercent() : Int {
    val percent = (progress * 100).roundToInt()
    return if (percent == 100 && !finished) 99 else percent
  }
}

/**
 * <code>UIController</code> contains methods for filling inspection widget popup and
 * reacting to changes in the popup.
 * Created lazily only when needed and once for every <code>AnalyzerStatus</code> instance.
 */
interface UIController {
  /**
   * Returns <code>true</code> if the inspection widget can be visible as a toolbar or
   * <code>false</code> if it can be visible as an icon above the scrollbar only.
   */
  fun enableToolbar() : Boolean

  /**
   * Contains all possible actions in the settings menu. The <code>List</code> is wrapped
   * in ActionGroup at the UI creation level in <code>EditorMarkupModelImpl</code>
   */
  fun getActions() : List<AnAction>

  /**
   * Lists possible <code>InspectionLevel</code>s for the particular file.
   */
  fun getAvailableLevels() : List<InspectionsLevel>

  /**
   * Lists highlight levels for the particular file per language if the file
   * contains several languages.
   */
  fun getHighlightLevels() : List<LanguageHighlightLevel>

  /**
   * Saves the <code>LanguageHighlightLevel</code> for the file.
   */
  fun setHighLightLevel(newLevels: LanguageHighlightLevel)

  /**
   * Adds panels coming from <code>com.intellij.hectorComponentProvider</code> EP providers to
   * the inspection widget popup.
   */
  fun fillHectorPanels(container: Container, gc: GridBag)

  /**
   * Can the inspection widget popup be closed. Might be necessary to complete some
   * settings in hector panels before closing the popup.
   * If a panel can be closed and is modified then the settings are applied for the panel.
   */
  fun canClosePopup() : Boolean

  /**
   * Called after the popup has been closed. Usually used to dispose resources held by
   * hector panels.
   */
  fun onClosePopup()
}

/**
 * Container containing all necessary information for rendering TrafficLightRenderer.
 * Instance is created each time <code>ErrorStripeRenderer.getStatus</code> is called.
 */
class AnalyzerStatus(val icon: Icon, title: String, details: String, controllerCreator: () -> UIController) {
  /**
   * Main panel title
   */
  val title = XmlStringUtil.wrapInHtml(title)

  /**
   * Possible details
   */
  val details = if (details.isNotEmpty()) XmlStringUtil.wrapInHtml(details) else ""

  /**
   * Lazy UI controller getter. Call only when you do need access to the UI details.
   */
  val controller : UIController by lazy(LazyThreadSafetyMode.NONE) { controllerCreator() }

  var showNavigation : Boolean = false
  var expandedIcon: Icon = icon
  var passes : List<PassWrapper> = Collections.emptyList()

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }

  fun withExpandedIcon(icon: Icon): AnalyzerStatus {
    expandedIcon = icon
    return this
  }

  fun withPasses(passes: List<PassWrapper>) : AnalyzerStatus {
    this.passes = passes
    return this
  }

  companion object {
    /**
     * Utility comparator which takes into account only valuable fields.
     * For example the whole UI controller is ignored.
     */
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