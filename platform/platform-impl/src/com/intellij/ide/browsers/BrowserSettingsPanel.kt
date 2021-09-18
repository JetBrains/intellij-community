// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.canUseSystemDefaultBrowserPolicy
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Comparing
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.LocalPathCellEditor
import com.intellij.util.ui.table.IconTableCellRenderer
import com.intellij.util.ui.table.TableModelEditor
import com.intellij.util.ui.table.TableModelEditor.DialogItemEditor
import com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo
import java.awt.event.ItemEvent
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class BrowserSettingsPanel {

  private lateinit var browsersTable: JComponent
  private lateinit var browsersEditor: TableModelEditor<ConfigurableWebBrowser>
  private lateinit var alternativeBrowserPathField: TextFieldWithBrowseButton
  private lateinit var defaultBrowserPolicyComboBox: ComboBox<DefaultBrowserPolicy>
  private lateinit var serverReloadModeComboBox: ComboBox<ReloadMode>
  private lateinit var previewReloadModeComboBox: ComboBox<ReloadMode>
  private lateinit var showBrowserHover: JBCheckBox
  private lateinit var showBrowserHoverXml: JBCheckBox
  private var customPathValue: String? = null

  private val root: JPanel = panel {
    val itemEditor: DialogItemEditor<ConfigurableWebBrowser> = object : DialogItemEditor<ConfigurableWebBrowser> {
      override fun getItemClass(): Class<ConfigurableWebBrowser> {
        return ConfigurableWebBrowser::class.java
      }

      override fun clone(item: ConfigurableWebBrowser, forInPlaceEditing: Boolean): ConfigurableWebBrowser {
        return ConfigurableWebBrowser(if (forInPlaceEditing) item.id else UUID.randomUUID(),
                                      item.family, item.name, item.path, item.isActive,
                                      if (forInPlaceEditing) item.specificSettings else cloneSettings(item))
      }

      override fun edit(browser: ConfigurableWebBrowser,
                        mutator: Function<in ConfigurableWebBrowser, out ConfigurableWebBrowser>,
                        isAdd: Boolean) {
        val settings = cloneSettings(browser)
        if (settings != null && ShowSettingsUtil.getInstance().editConfigurable(browsersTable, settings.createConfigurable())) {
          mutator.`fun`(browser).specificSettings = settings
        }
      }

      private fun cloneSettings(browser: ConfigurableWebBrowser): BrowserSpecificSettings? {
        val settings = browser.specificSettings ?: return null
        val newSettings = browser.family.createBrowserSpecificSettings()!!
        TableModelEditor.cloneUsingXmlSerialization(settings, newSettings)
        return newSettings
      }

      override fun applyEdited(oldItem: ConfigurableWebBrowser, newItem: ConfigurableWebBrowser) {
        oldItem.specificSettings = newItem.specificSettings
      }

      override fun isEditable(browser: ConfigurableWebBrowser): Boolean {
        return browser.specificSettings != null
      }

      override fun isRemovable(item: ConfigurableWebBrowser): Boolean {
        return !WebBrowserManager.getInstance().isPredefinedBrowser(item)
      }
    }

    browsersEditor = TableModelEditor(COLUMNS, itemEditor, IdeBundle.message("settings.browsers.no.web.browsers.configured"))
      .modelListener(object : TableModelEditor.DataChangedListener<ConfigurableWebBrowser?>() {
        override fun tableChanged(event: TableModelEvent) {
          update()
        }

        override fun dataChanged(columnInfo: ColumnInfo<ConfigurableWebBrowser?, *>, rowIndex: Int) {
          if (columnInfo === PATH_COLUMN_INFO || columnInfo === ACTIVE_COLUMN_INFO) {
            update()
          }
        }

        private fun update() {
          if (defaultBrowser == DefaultBrowserPolicy.FIRST) {
            setCustomPathToFirstListed()
          }
        }
      })

    row {
      cell(isFullWidth = true) {
        component(browsersEditor.createComponent()).constraints(grow, pushY).applyToComponent { browsersTable = this }
      }
    }

    row {
      cell(isFullWidth = true) {
        val defaultBrowserPolicies = ArrayList<DefaultBrowserPolicy>()
        if (canUseSystemDefaultBrowserPolicy()) {
          defaultBrowserPolicies.add(DefaultBrowserPolicy.SYSTEM)
        }
        defaultBrowserPolicies.add(DefaultBrowserPolicy.FIRST)
        defaultBrowserPolicies.add(DefaultBrowserPolicy.ALTERNATIVE)

        label(IdeBundle.message("settings.browsers.default.browser"))
        component(ComboBox(CollectionComboBoxModel(defaultBrowserPolicies))).applyToComponent {
          this.renderer = SimpleListCellRenderer.create("") { value: DefaultBrowserPolicy ->
            when (value) {
              DefaultBrowserPolicy.SYSTEM -> IdeBundle.message("settings.browsers.system.default")
              DefaultBrowserPolicy.FIRST -> IdeBundle.message("settings.browsers.first.listed")
              DefaultBrowserPolicy.ALTERNATIVE -> IdeBundle.message("settings.browsers.custom.path")
            }
          }
          this.addItemListener { e ->
            val customPathEnabled = e.item === DefaultBrowserPolicy.ALTERNATIVE
            if (e.stateChange == ItemEvent.DESELECTED) {
              if (customPathEnabled) {
                customPathValue = alternativeBrowserPathField.text
              }
            }
            else if (e.stateChange == ItemEvent.SELECTED) {
              alternativeBrowserPathField.isEnabled = customPathEnabled
              updateCustomPathTextFieldValue(e.item as DefaultBrowserPolicy)
            }
          }
          defaultBrowserPolicyComboBox = this
        }
        component(TextFieldWithBrowseButton()).applyToComponent {
          this.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, APP_FILE_CHOOSER_DESCRIPTOR)
          alternativeBrowserPathField = this
        }
      }
    }

    titledRow(IdeBundle.message("settings.browsers.show.browser.popup.in.the.editor")) {
      row {
        checkBox(IdeBundle.message("settings.browsers.show.browser.popup.html")).applyToComponent {
          showBrowserHover = this
        }
      }
      row {
        checkBox(IdeBundle.message("settings.browsers.show.browser.popup.xml")).applyToComponent {
          showBrowserHoverXml = this
        }
      }
    }

    titledRow(IdeBundle.message("settings.browsers.reload.behavior")) {
      row(IdeBundle.message("setting.value.reload.mode.server")) {
        component(ComboBox(DefaultComboBoxModel(ReloadMode.values()))).applyToComponent {
          this.renderer = SimpleListCellRenderer.create("") { it.title }
          serverReloadModeComboBox = this
        }
      }
      row(IdeBundle.message("setting.value.reload.mode.preview")) {
        component(ComboBox(DefaultComboBoxModel(ReloadMode.values()))).applyToComponent {
          this.renderer = SimpleListCellRenderer.create("") { it.title }
          previewReloadModeComboBox = this
        }
      }
    }
  }

  private fun updateCustomPathTextFieldValue(browser: DefaultBrowserPolicy) {
    when (browser) {
      DefaultBrowserPolicy.ALTERNATIVE -> {
        alternativeBrowserPathField.setText(customPathValue)
      }
      DefaultBrowserPolicy.FIRST -> {
        setCustomPathToFirstListed()
      }
      else -> {
        alternativeBrowserPathField.text = ""
      }
    }
  }

  private fun setCustomPathToFirstListed() {
    val model = browsersEditor.model
    var i = 0
    val n = model.rowCount
    while (i < n) {
      val browser = model.getRowValue(i)
      if (browser.isActive && browser.path != null) {
        alternativeBrowserPathField.setText(browser.path)
        return
      }
      i++
    }
    alternativeBrowserPathField.text = ""
  }

  val component: JPanel
    get() = root

  val isModified: Boolean
    get() {
      val browserManager = WebBrowserManager.getInstance()
      val generalSettings = GeneralSettings.getInstance()
      val defaultBrowserPolicy = defaultBrowser
      if (getDefaultBrowserPolicy(browserManager) != defaultBrowserPolicy ||
          browserManager.isShowBrowserHover != showBrowserHover.isSelected ||
          browserManager.isShowBrowserHoverXml != showBrowserHoverXml.isSelected ||
          browserManager.getWebPreviewReloadMode() !== previewReloadModeComboBox.item ||
          browserManager.getWebServerReloadMode() !== serverReloadModeComboBox.item) {
        return true
      }
      return if (defaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE &&
                 !Comparing.strEqual(generalSettings.browserPath, alternativeBrowserPathField.text)) {
        true
      }
      else browsersEditor.isModified
    }

  fun apply() {
    val settings = GeneralSettings.getInstance()
    settings.isUseDefaultBrowser = defaultBrowser == DefaultBrowserPolicy.SYSTEM
    if (alternativeBrowserPathField.isEnabled) {
      settings.browserPath = alternativeBrowserPathField.text
    }
    val browserManager = WebBrowserManager.getInstance()
    browserManager.isShowBrowserHover = showBrowserHover.isSelected
    browserManager.isShowBrowserHoverXml = showBrowserHoverXml.isSelected
    browserManager.defaultBrowserPolicy = defaultBrowser
    browserManager.webPreviewReloadMode = previewReloadModeComboBox.item
    browserManager.webServerReloadMode = serverReloadModeComboBox.item
    browserManager.list = browsersEditor.apply()
  }

  private val defaultBrowser: DefaultBrowserPolicy
    get() = defaultBrowserPolicyComboBox.selectedItem as DefaultBrowserPolicy

  fun reset() {
    val browserManager = WebBrowserManager.getInstance()
    val effectiveDefaultBrowserPolicy = getDefaultBrowserPolicy(browserManager)
    defaultBrowserPolicyComboBox.selectedItem = effectiveDefaultBrowserPolicy
    previewReloadModeComboBox.item = browserManager.getWebPreviewReloadMode()
    serverReloadModeComboBox.item = browserManager.getWebServerReloadMode()
    val settings = GeneralSettings.getInstance()
    showBrowserHover.isSelected = browserManager.isShowBrowserHover
    showBrowserHoverXml.isSelected = browserManager.isShowBrowserHoverXml
    browsersEditor.reset(browserManager.list)
    customPathValue = settings.browserPath
    alternativeBrowserPathField.isEnabled = effectiveDefaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE
    updateCustomPathTextFieldValue(effectiveDefaultBrowserPolicy)
  }

  fun selectBrowser(browser: WebBrowser) {
    if (browser is ConfigurableWebBrowser) {
      browsersEditor.selectItem(browser)
    }
  }

  companion object {
    private val APP_FILE_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
    private val PATH_COLUMN_INFO: EditableColumnInfo<ConfigurableWebBrowser, String> = object : EditableColumnInfo<ConfigurableWebBrowser, String>(
      IdeBundle.message("settings.browsers.column.path")) {
      override fun valueOf(item: ConfigurableWebBrowser): String? {
        return PathUtil.toSystemDependentName(item.path)
      }

      override fun setValue(item: ConfigurableWebBrowser, value: String) {
        item.path = value
      }

      override fun getEditor(item: ConfigurableWebBrowser): TableCellEditor? {
        return LocalPathCellEditor().fileChooserDescriptor(APP_FILE_CHOOSER_DESCRIPTOR).normalizePath(true)
      }
    }
    private val ACTIVE_COLUMN_INFO: EditableColumnInfo<ConfigurableWebBrowser, Boolean> = object : EditableColumnInfo<ConfigurableWebBrowser, Boolean>() {
      override fun getColumnClass(): Class<*> {
        return Boolean::class.java
      }

      override fun valueOf(item: ConfigurableWebBrowser): Boolean {
        return item.isActive
      }

      override fun setValue(item: ConfigurableWebBrowser, value: Boolean) {
        item.isActive = value
      }
    }

    private val COLUMNS = arrayOf<ColumnInfo<*, *>>(
      ACTIVE_COLUMN_INFO,
      object : EditableColumnInfo<ConfigurableWebBrowser, String>(
        IdeBundle.message("settings.browsers.column.name")) {
        override fun valueOf(item: ConfigurableWebBrowser): String {
          return item.name
        }

        override fun setValue(item: ConfigurableWebBrowser, value: String) {
          item.name = value
        }
      },
      object : ColumnInfo<ConfigurableWebBrowser, BrowserFamily>(
        IdeBundle.message("settings.browsers.column.family")) {
        override fun getColumnClass(): Class<*> {
          return BrowserFamily::class.java
        }

        override fun valueOf(item: ConfigurableWebBrowser): BrowserFamily {
          return item.family
        }

        override fun setValue(item: ConfigurableWebBrowser, value: BrowserFamily) {
          item.family = value
          item.specificSettings = value.createBrowserSpecificSettings()
        }

        override fun getRenderer(item: ConfigurableWebBrowser): TableCellRenderer {
          return IconTableCellRenderer.ICONABLE
        }

        override fun isCellEditable(item: ConfigurableWebBrowser): Boolean {
          return !WebBrowserManager.getInstance().isPredefinedBrowser(item)
        }
      },
      PATH_COLUMN_INFO
    )

    private fun getDefaultBrowserPolicy(manager: WebBrowserManager): DefaultBrowserPolicy {
      val policy = manager.getDefaultBrowserPolicy()
      return if (policy != DefaultBrowserPolicy.SYSTEM || canUseSystemDefaultBrowserPolicy()) {
        policy
      }
      else DefaultBrowserPolicy.ALTERNATIVE
      // if system default browser policy cannot be used
    }
  }
}