// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.awt.Container
import java.util.*
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * Inspection highlight level with string representations bound to resources for i18n.
 */
enum class InspectionsLevel(@PropertyKey(resourceBundle = EditorBundle.BUNDLE) private val nameKey: String,
                            @PropertyKey(resourceBundle = EditorBundle.BUNDLE) private val descriptionKey: String,
) {
  NONE("iw.level.none.name","iw.level.none.description"),
  SYNTAX("iw.level.syntax.name", "iw.level.syntax.description"),
  ESSENTIAL("iw.level.essential.name", "iw.level.essential.description"),
  ALL("iw.level.all.name", "iw.level.all.description");

  @Nls
  override fun toString(): String = EditorBundle.message(nameKey)

  val description: @Nls String
    get() = EditorBundle.message(descriptionKey)
}

/*
 * Per language highlight level
 */
data class LanguageHighlightLevel(@NlsSafe @get:NlsSafe val langID: String, val level: InspectionsLevel)

/**
 * Light wrapper for <code>ProgressableTextEditorHighlightingPass</code> with only essential UI data.
 */
data class PassWrapper(@Nls @get:Nls val presentableName: String, val progress: Double, val finished: Boolean) {
  fun toPercent() : Int {
    val percent = (progress * 100).roundToInt()
    return if (percent == 100 && !finished) 99 else percent
  }
}

/**
 * Type of the analyzing status that's taking place.
 */
enum class AnalyzingType {
  COMPLETE, // Analyzing complete, final results are available or none if OFF or in PowerSave mode
  SUSPENDED, // Analyzing suspended for long process like indexing
  PARTIAL,  // Analyzing has partial results available for displaying
  EMPTY     // Analyzing in progress but no information is available
}
/**
 * Severity status item containing text (not necessarily a number) possible icon and details text for popup
 */
data class StatusItem @JvmOverloads constructor(@Nls @get:Nls val text: String, val icon: Icon? = null, val detailsText: String? = null)

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
  fun setHighLightLevel(newLevel: LanguageHighlightLevel)

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

  fun toggleProblemsView()
}

/**
 * Container containing all necessary information for rendering TrafficLightRenderer.
 * Instance is created each time <code>ErrorStripeRenderer.getStatus</code> is called.
 */
class AnalyzerStatus(val icon: Icon, @Nls @get:Nls val title: String, @Nls @get:Nls val details: String, controllerCreator: () -> UIController) {
  /**
   * Lazy UI controller getter. Call only when you do need access to the UI details.
   */
  val controller : UIController by lazy(LazyThreadSafetyMode.NONE) { controllerCreator() }

  var showNavigation : Boolean = false
  var expandedStatus: List<StatusItem> = emptyList()
  var passes : List<PassWrapper> = emptyList()
  var analyzingType : AnalyzingType = AnalyzingType.COMPLETE
    private set
  private var textStatus: Boolean = false

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }

  fun withTextStatus(@Nls status: String): AnalyzerStatus {
    expandedStatus = Collections.singletonList(StatusItem(status))
    textStatus = true
    return this
  }

  fun withExpandedStatus(status: List<StatusItem>): AnalyzerStatus {
    expandedStatus = status
    return this
  }

  fun withAnalyzingType(type: AnalyzingType) : AnalyzerStatus {
    analyzingType = type
    return this
  }

  fun withPasses(passes: List<PassWrapper>) : AnalyzerStatus {
    this.passes = passes
    return this
  }

  fun isTextStatus() : Boolean = textStatus

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
    val EMPTY by lazy(LazyThreadSafetyMode.NONE) {
      AnalyzerStatus(EmptyIcon.ICON_0, "", "") { EmptyController }
    }

    @JvmStatic
    fun isEmpty(status: AnalyzerStatus) = status == EMPTY

    @JvmStatic
    val EmptyController = object : UIController {
      override fun enableToolbar(): Boolean = false
      override fun getActions(): List<AnAction> = emptyList()
      override fun getAvailableLevels(): List<InspectionsLevel> = emptyList()
      override fun getHighlightLevels(): List<LanguageHighlightLevel> = emptyList()
      override fun setHighLightLevel(newLevel: LanguageHighlightLevel) {}
      override fun fillHectorPanels(container: Container, gc: GridBag) {}
      override fun canClosePopup(): Boolean = true
      override fun onClosePopup() {}
      override fun toggleProblemsView() {}
    }
  }
}