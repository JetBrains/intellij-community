// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.LanguageAndRegionBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.util.*
import javax.swing.*
import javax.swing.border.Border

private val localeMappings = mapOf(Locale.CHINA to listOf("zh-CN", "CN"), Locale.JAPANESE to listOf("ja", "JP"),
                                   Locale.KOREAN to listOf("ko", "KR", "KP"))

private class LanguageAndRegionDialog(private var selectedLanguage: Locale) : DialogWrapper(null, null, true, IdeModalityType.IDE, false) {
  private var region = getRegionForLocale(selectedLanguage)
  private val TOP_BOTTOM_BORDERS = 4
  private val LEFT_RIGHT_BORDERS = 16
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
    createButton(false, getRegionLabel(region), AllIcons.General.ChevronDown) { createRegionPopup(it) })

  private fun getNextButton() = ButtonPanel(createButton(true, getMessageBundle().getString("button.next"), null) { this.doOKAction() })

  override fun createContentPaneBorder(): Border {
    return JBUI.Borders.empty()
  }

  private fun createRegionPopup(button: JButton) {
    val regions = Region.entries.sortedBy { it.displayOrdinal }
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(regions)
      .setRequestFocus(true)
      .setRenderer { _, value, _, _, _ ->
        if (value == null) return@setRenderer JLabel()
        val label = JBLabel(getRegionName(value), null, SwingConstants.LEFT)
        label.border = JBUI.Borders.empty(TOP_BOTTOM_BORDERS, LEFT_RIGHT_BORDERS)
        return@setRenderer label
      }
      .setMinSize(Dimension(280, 100))
      .setResizable(false)
      .setCancelOnClickOutside(true)
      .setItemChosenCallback {
        region = it
        button.text = getRegionLabel(it)
      }
      .createPopup()
    popup.show(RelativePoint.getSouthWestOf(button))
  }

  private fun createLanguagePopup(button: JButton) {
    val locales = mutableListOf(Locale.ENGLISH) + localeMappings.keys
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(locales)
      .setRequestFocus(true)
      .setRenderer { _, value, _, _, _ ->
        if (value == null) return@setRenderer JLabel()
        val label = JBLabel(getLocaleName(value), null, SwingConstants.LEFT)
        label.border = JBUI.Borders.empty(TOP_BOTTOM_BORDERS, LEFT_RIGHT_BORDERS)
        return@setRenderer label
      }
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

  private fun getRegionForLocale(locale: Locale): Region {
    return when (locale) {
      Locale.CHINA -> Region.CHINA
      Locale.JAPANESE, Locale.KOREAN -> Region.APAC
      else -> Region.NOT_SET
    }
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
    RegionSettings.setRegion(region)
    super.doOKAction()
  }

  private fun getMessageBundle() = DynamicBundle.getResourceBundleLocalized(this::class.java.classLoader, LanguageAndRegionBundle.BUNDLE_FQN, selectedLanguage)
}


internal fun getLanguageAndRegionDialogIfNeeded(): (suspend () -> Boolean)? {
  val locale = Locale.getDefault()
  val matchingLanguage = localeMappings.values.flatten().find { locale.toLanguageTag().contains(it) }
  if (matchingLanguage == null) return null
  return suspend {
    withContext(RawSwingDispatcher) {
      val matchingLocale = localeMappings.entries.find { it.value.contains(matchingLanguage) }!!.key
      LanguageAndRegionDialog(matchingLocale).showAndGet()
    }
  }
}
