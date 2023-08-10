// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.lang.LangBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.Processor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
import java.awt.Color
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.text.JTextComponent

@Internal
interface SearchEverywhereSpellingCorrector {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SearchEverywhereSpellingCorrectorFactory>("com.intellij.searchEverywhereSpellingCorrector")

    @JvmStatic
    @Contract("null -> null")
    fun getInstance(project: Project?): SearchEverywhereSpellingCorrector? {
      if (project == null) return null

      val extensions = EP_NAME.extensionList
      return extensions.firstOrNull()
        ?.takeIf { it.isAvailable(project) }
        ?.create(project)
    }
  }

  fun isAvailableInTab(tabId: String): Boolean

  fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult
}

@Internal
interface SearchEverywhereSpellingCorrectorFactory {
  fun isAvailable(project: Project): Boolean

  fun create(project: Project): SearchEverywhereSpellingCorrector
}

@Internal
class SearchEverywhereSpellingCorrectorContributor(private val textComponent: JTextComponent) : SearchEverywhereContributor<SearchEverywhereSpellCheckResult.Correction> {
  override fun getSearchProviderId(): String = this::class.java.simpleName
  override fun getGroupName(): String = LangBundle.message("search.everywhere.typos.group.name")
  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = false
  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in SearchEverywhereSpellCheckResult.Correction>) { }
  override fun processSelectedItem(selected: SearchEverywhereSpellCheckResult.Correction, modifiers: Int, searchText: String): Boolean {
    textComponent.text = selected.correction
    return false
  }
  override fun getElementsRenderer(): ListCellRenderer<in SearchEverywhereSpellCheckResult.Correction> = SpellingCorrectionListCellRenderer()
  override fun getDataForItem(element: SearchEverywhereSpellCheckResult.Correction, dataId: String): Any? = null

  private class SpellingCorrectionListCellRenderer : SimpleColoredComponent(), ListCellRenderer<SearchEverywhereSpellCheckResult.Correction> {
    private var selected = false
    private var foreground: Color? = null
    private var selectionForeground: Color? = null
    override fun getListCellRendererComponent(list: JList<out SearchEverywhereSpellCheckResult.Correction>,
                                              value: SearchEverywhereSpellCheckResult.Correction,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      clear()
      ipad.right = if (UIUtil.isUnderWin10LookAndFeel()) 0 else scale(UIUtil.getListCellHPadding())
      ipad.left = ipad.right
      setPaintFocusBorder(false)
      setFocusBorderAroundIcon(true)
      font = list.font
      icon = EmptyIcon.ICON_16
      selected = isSelected
      foreground = if (isEnabled) list.foreground else UIUtil.getLabelDisabledForeground()
      selectionForeground = list.selectionForeground
      background = if (UIUtil.isUnderWin10LookAndFeel()) {
        if (isSelected) list.selectionBackground else list.background
      }
      else {
        if (isSelected) list.selectionBackground else null
      }
      val baseStyle = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.foreground)
      appendHTML(value.presentationText, baseStyle)
      return this
    }

    override fun append(fragment: String, attributes: SimpleTextAttributes, isMainText: Boolean) {
      if (selected) {
        super.append(fragment, SimpleTextAttributes(attributes.style, selectionForeground), isMainText)
      }
      else if (attributes.fgColor == null) {
        super.append(fragment, attributes.derive(-1, foreground, null, null), isMainText)
      }
      else {
        super.append(fragment, attributes, isMainText)
      }
    }
  }
}