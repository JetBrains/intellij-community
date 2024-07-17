// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.LanguageAndRegionBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.*
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.util.*
import javax.swing.*
import javax.swing.border.Border

private val languageMapping = mapOf(Locale.CHINA to listOf("zh-CN", "zh-Hans"), Locale.JAPANESE to listOf("ja"),
                                    Locale.KOREAN to listOf("ko"))
private val regionMapping = mapOf(Region.CHINA to "CN")

private class LanguageAndRegionDialog(private var selectedLanguage: Locale, private var selectedRegion: Region) : DialogWrapper(null, null, true, IdeModalityType.IDE, false) {
  init {
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    val centerPanel = object : JPanel(VerticalLayout(JBUI.scale(10), SwingConstants.CENTER)) {
      override fun getComponentGraphics(g: Graphics?): Graphics {
        return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
      }
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    centerPanel.preferredSize = JBDimension(640, 470)
    val header = JLabel(getMessageBundle().getString("title.language.and.region")).apply {
      font = JBFont.h1()
      alignmentX = JLabel.CENTER_ALIGNMENT
    }

    centerPanel.add(panel {
      row {
        cell(header).align(Align.CENTER)
      }
      row {
        text(getMessageBundle().getString("description.language.and.region"))
      }
    }.withPreferredWidth(350), VerticalLayout.CENTER)

    val buttonsPanel = JPanel(VerticalLayout(JBUI.scale(3), SwingConstants.CENTER))
    buttonsPanel.add(getLanguageButton())
    buttonsPanel.add(getRegionButton())
    buttonsPanel.add(getNextButton())
    centerPanel.add(buttonsPanel, VerticalLayout.CENTER)
    return centerPanel
  }

  private fun getLanguageButton() =
    ButtonPanel(createButton(false, getLocaleName(selectedLanguage), AllIcons.General.ChevronDown) { createLanguagePopup(it) })

  private fun getRegionButton() = ButtonPanel(
    createButton(false, getRegionLabel(selectedRegion), AllIcons.General.ChevronDown) { createRegionPopup(it) })

  private fun getNextButton() = ButtonPanel(createButton(true, getMessageBundle().getString("button.next"), null) { this.doOKAction() })

  override fun createContentPaneBorder(): Border {
    return JBUI.Borders.empty()
  }

  private fun createRegionPopup(button: JButton) {
    val regions = Region.entries.sortedBy { it.displayOrdinal }
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(regions)
      .setRequestFocus(true)
      .setRenderer { list, value, _, selected, _ ->
        createRendererComponent(getRegionName(value), list, selected)
      }
      .setSelectedValue(selectedRegion, true)
      .setMinSize(Dimension(280, 100))
      .setResizable(false)
      .setCancelOnClickOutside(true)
      .setItemChosenCallback {
        selectedRegion = it
        button.text = getRegionLabel(it)
      }
      .createPopup()
    popup.show(RelativePoint.getSouthWestOf(button))
  }

  private fun createLanguagePopup(button: JButton) {
    val locales = mutableListOf(Locale.ENGLISH) + languageMapping.keys
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(locales)
      .setRequestFocus(true)
      .setRenderer { list, value, _, selected, _ ->
        createRendererComponent(getLocaleName(value), list, selected)
      }
      .setSelectedValue(selectedLanguage, true)
      .setMinSize(Dimension(280, 100))
      .setResizable(false)
      .setCancelOnClickOutside(true)
      .setItemChosenCallback {
        selectedLanguage = it
        contentPanel.removeAll()
        val panel = createCenterPanel()
        contentPanel.add(panel, BorderLayout.CENTER)
        contentPanel.doLayout()
        contentPanel.revalidate()
        contentPanel.repaint()
      }
      .createPopup()
    popup.show(RelativePoint.getSouthWestOf(button))
  }

  private fun createRendererComponent(@Nls value: String, list: JComponent, selected: Boolean): JComponent {
    val label = JBLabel(value, null, SwingConstants.LEFT)
    val panel = SelectablePanel.wrap(label, list.background)
    if (selected) {
      panel.selectionColor = UIUtil.getListSelectionBackground(true)
    }
    PopupUtil.configListRendererFixedHeight(panel)
    return panel
  }

  @Nls
  private fun getLocaleName(locale: Locale): String {
    return getMessageBundle().getString("language." + locale.toLanguageTag().replace("-", ""))
  }

  @Nls
  private fun getRegionName(region: Region): String {
    return getMessageBundle().getString(region.displayKey)
  }
  
  private fun getRegionLabel(region: Region): String {
    return getMessageBundle().getString(if (region == Region.NOT_SET) "title.region.not.set.label" else region.displayKey)
  }


  override fun doOKAction() {
    LocalizationStateService.getInstance()?.setSelectedLocale(selectedLanguage.toLanguageTag())
    RegionSettings.setRegion(selectedRegion)
    super.doOKAction()
  }

  private fun getMessageBundle() = DynamicBundle.getResourceBundleLocalized(this::class.java.classLoader, LanguageAndRegionBundle.BUNDLE_FQN, selectedLanguage)
}


internal fun getLanguageAndRegionDialogIfNeeded(document: EndUserAgreement.Document?): (suspend () -> Boolean)? {
  if (document == null) return null
  val locale = Locale.getDefault()
  val matchingLocale = languageMapping.keys.find { language -> languageMapping[language]?.any { locale.toLanguageTag().contains(it) } == true }
                       ?: Locale.ENGLISH
  var matchingRegion = regionMapping.keys.find { locale.country == regionMapping[it] } ?: Region.NOT_SET
  if (matchingRegion == Region.NOT_SET && matchingLocale == Locale.ENGLISH) return null
  return suspend {
    withContext(RawSwingDispatcher) {
      LanguageAndRegionDialog(matchingLocale, matchingRegion).showAndGet()
    }
  }
}
