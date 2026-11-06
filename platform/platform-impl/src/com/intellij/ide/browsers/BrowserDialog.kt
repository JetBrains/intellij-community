// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.PathUtil
import java.util.UUID
import javax.swing.JComponent
import javax.swing.ListCellRenderer

/**
 * The dialog that adds a browser to the browser list, or edits a browser of the list.
 *
 * On OK, it writes the name, the path, the family, and the family settings to [browser].
 * So give it a copy of a row of the list.
 * The family settings come from [BrowserSpecificSettings.createConfigurable], for example the Chrome command line options.
 * When [isFamilyEditable] is `false`, the family stays as it is.
 * A path change fills an empty name and family from [detectBrowserType].
 */
internal class BrowserDialog(
  parent: JComponent,
  private val browser: ConfigurableWebBrowser,
  isAdd: Boolean,
  private val isFamilyEditable: Boolean,
) : DialogWrapper(parent, true) {
  private class FamilySettings(val settings: BrowserSpecificSettings, val configurable: Configurable, val component: JComponent?)

  private val familySettings = HashMap<BrowserFamily, FamilySettings>()
  private var family: BrowserFamily? = browser.family
  private var detectedBrowser: KnownBrowser? = null
  private lateinit var nameField: JBTextField
  private lateinit var familyComboBox: ComboBox<BrowserFamily?>
  private lateinit var settingsPlaceholder: Placeholder

  init {
    title = IdeBundle.message(if (isAdd) "settings.browsers.dialog.title.add" else "settings.browsers.dialog.title.edit")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = panel {
      row(IdeBundle.message("settings.browsers.dialog.name")) {
        nameField = textField()
          .bindText(browser::getName, browser::setName)
          .columns(COLUMNS_LARGE)
          .align(AlignX.FILL)
          .focused()
          .validationOnApply { if (it.text.isBlank()) error(IdeBundle.message("settings.browsers.dialog.name.empty")) else null }
          .component
      }
      row(IdeBundle.message("settings.browsers.dialog.path")) {
        textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor())
          .bindText({ PathUtil.toSystemDependentName(browser.path) ?: "" }, { browser.path = it })
          .align(AlignX.FILL)
          .validationOnApply { if (it.text.isBlank()) error(IdeBundle.message("settings.browsers.dialog.path.empty")) else null }
          .onChanged { detectBrowser(it.text) }
      }
      row(IdeBundle.message("settings.browsers.dialog.family")) {
        familyComboBox = comboBox(listOf(null) + BrowserFamily.entries, browserFamilyRenderer())
          .bindItem(browser::getFamily, browser::setFamily)
          .enabled(isFamilyEditable)
          .component
        // not onChanged, because it ignores the selection of the null item
        familyComboBox.addActionListener {
          family = familyComboBox.item
          showFamilySettings()
        }
      }
      row {
        settingsPlaceholder = placeholder().align(AlignX.FILL)
      }
    }
    showFamilySettings()
    return panel
  }

  /** Fills the name and the family when they are empty, or when they are what the previous path gave. */
  private fun detectBrowser(path: String) {
    val detected = detectBrowserType(ConfigurableWebBrowser(UUID.randomUUID(), null, "", path, true, null))
    val previous = detectedBrowser
    detectedBrowser = detected
    if (detected == null) {
      return
    }
    if (nameField.text.isBlank() || nameField.text == previous?.macAppName) {
      nameField.text = detected.macAppName
    }
    if (isFamilyEditable && (familyComboBox.item == null || familyComboBox.item == previous?.family)) {
      familyComboBox.item = detected.family
    }
  }

  private fun familySettingsOf(family: BrowserFamily?): FamilySettings? {
    if (family == null) {
      return null
    }
    familySettings[family]?.let { return it }
    val settings = (if (family == browser.family) browser.specificSettings else null) ?: family.createBrowserSpecificSettings() ?: return null
    val configurable = settings.createConfigurable()
    val component = configurable.createComponent()
    configurable.reset()
    return FamilySettings(settings, configurable, component).also { familySettings[family] = it }
  }

  private fun showFamilySettings() {
    settingsPlaceholder.component = familySettingsOf(family)?.component
    if (isShowing) {
      pack()
    }
  }

  override fun doOKAction() {
    try {
      familySettingsOf(family)?.configurable?.apply()
    }
    catch (e: ConfigurationException) {
      setErrorText(e.messageHtml.toString())
      return
    }
    super.doOKAction()
  }

  override fun applyFields() {
    super.applyFields()
    browser.specificSettings = familySettingsOf(family)?.settings
  }

  override fun dispose() {
    familySettings.values.forEach { it.configurable.disposeUIResources() }
    super.dispose()
  }
}

/** Renders a [BrowserFamily], or "Not specified" for `null`. */
internal fun browserFamilyRenderer(): ListCellRenderer<BrowserFamily?> =
  listCellRenderer(IdeBundle.message("settings.browsers.family.not.specified")) {
    icon(value.icon)
    text(value.getName())
  }
