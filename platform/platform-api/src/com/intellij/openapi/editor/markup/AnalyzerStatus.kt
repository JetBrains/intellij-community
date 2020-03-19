// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorBundle
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.PropertyKey
import java.awt.Container
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * Inspection highlight level with string representations bound to resources for i18n.
 */
enum class InspectionsLevel(@PropertyKey(resourceBundle = EditorBundle.BUNDLE) private val bundleKey: String) {
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
 * Standard (predefined) expanded status that's printed as text in the inspection widget component.
 */
enum class StandardStatus(private val bundleKey: String) {
  NONE(""),
  OFF("iw.status.off"),
  INDEXING("iw.status.indexing"),
  ANALYZING("iw.status.analyzing");

  override fun toString(): String = if (bundleKey.isNotEmpty()) EditorBundle.message(bundleKey) else ""
}

/**
 * Severity status item containing text (not necessarily a number) and a possible icon
 */
data class StatusItem(val text: String, val icon: Icon?)

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

  fun openProblemsView()
}

/**
 * Container containing all necessary information for rendering TrafficLightRenderer.
 * Instance is created each time <code>ErrorStripeRenderer.getStatus</code> is called.
 */
class AnalyzerStatus(val icon: Icon, val title: String, val details: String, controllerCreator: () -> UIController) {
  /**
   * Lazy UI controller getter. Call only when you do need access to the UI details.
   */
  val controller : UIController by lazy(LazyThreadSafetyMode.NONE) { controllerCreator() }

  var showNavigation : Boolean = false
  var expandedStatus: List<StatusItem> = emptyList()
  var passes : List<PassWrapper> = emptyList()
  var standardStatus: StandardStatus = StandardStatus.NONE;

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }

  fun withExpandedStatus(status: List<StatusItem>): AnalyzerStatus {
    expandedStatus = status
    return this
  }

  fun withStandardStatus(status: StandardStatus): AnalyzerStatus {
    expandedStatus = listOf(StatusItem(status.toString(), null))
    standardStatus = status
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
      if (a == null && b == null) {
        return true
      }
      if (a == null || b == null) {
        return false
      }
      return a.icon == b.icon
             && a.expandedStatus == b.expandedStatus
             && a.title == b.title
             && a.details == b.details
             && a.showNavigation == b.showNavigation
             && a.passes == b.passes
    }

    /**
     * Default instance for classes that don't implement <code>ErrorStripeRenderer.getStatus</code>
     */
    @JvmStatic
    val DEFAULT by lazy(LazyThreadSafetyMode.NONE) {
      AnalyzerStatus(EmptyIcon.ICON_0, "", "") {
        object : UIController {
          override fun enableToolbar(): Boolean = false
          override fun getActions(): List<AnAction> = emptyList()
          override fun getAvailableLevels(): List<InspectionsLevel> = emptyList()
          override fun getHighlightLevels(): List<LanguageHighlightLevel> = emptyList()
          override fun setHighLightLevel(newLevels: LanguageHighlightLevel) {}
          override fun fillHectorPanels(container: Container, gc: GridBag) {}
          override fun canClosePopup(): Boolean = true
          override fun onClosePopup() {}
          override fun openProblemsView() {}
        }
      }
    }
  }
}