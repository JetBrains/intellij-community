// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.canUseSystemDefaultBrowserPolicy
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.LocalPathCellEditor
import com.intellij.util.ui.table.TableModelEditor
import com.intellij.util.ui.table.TableModelEditor.DialogItemEditor
import com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo
import java.util.UUID
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * An item of the "Default Browser" dropdown: [SystemDefaultBrowser], a browser of a [WebBrowserProvider], or a browser of the list.
 * For a browser of the list, [id] is the id of the original row, see [BrowserSettingsConfigurable.originalOf].
 */
internal data class BrowserChoice(val id: UUID, @NlsSafe val name: String, val icon: Icon) {
  companion object {
    fun systemDefault(): BrowserChoice = of(SystemDefaultBrowser)

    fun of(browser: WebBrowser): BrowserChoice = BrowserChoice(browser.id, browser.name, browser.icon)
  }
}

internal class BrowserSettingsConfigurable : UnnamedConfigurable {
  private lateinit var browsersTable: JComponent
  private lateinit var browsersEditor: TableModelEditor<ConfigurableWebBrowser>
  private val defaultBrowserModel = CollectionComboBoxModel<BrowserChoice>()
  private lateinit var serverReloadModeComboBox: ComboBox<ReloadMode>
  private lateinit var previewReloadModeComboBox: ComboBox<ReloadMode>
  private lateinit var showBrowserHover: JBCheckBox
  private lateinit var showBrowserHoverXml: JBCheckBox

  private var root: DialogPanel? = null

  /**
   * Returns the original row of [item]. The table edits a copy of the row with a new id, so a check of the id must look at the original.
   */
  private fun originalOf(item: ConfigurableWebBrowser): ConfigurableWebBrowser {
    var original = item
    browsersEditor.processModifiedItems { modified, initial ->
      if (modified === item) {
        original = initial
        false
      }
      else {
        true
      }
    }
    return original
  }

  /** The active browsers of the table that have a path. */
  private fun listedBrowsers(): List<BrowserChoice> =
    browsersEditor.model.items
      .filter { it.isActive && it.path != null }
      .map { BrowserChoice(originalOf(it).id, it.name, it.icon) }

  /** Fills the dropdown from the table and keeps the selected item. */
  private fun updateBrowserChoices() {
    val choice = defaultBrowserModel.selected
    val systemDefault = if (canUseSystemDefaultBrowserPolicy()) listOf(BrowserChoice.systemDefault()) else emptyList()
    val provided = WebBrowserProvider.getAvailableBrowsers().map { BrowserChoice.of(it) }
    val listed = listedBrowsers()
    defaultBrowserModel.replaceAll(buildList {
      addAll(systemDefault)
      addAll(provided)
      addAll(listed)
    })
    // the provided browsers come last, so a provider cannot take the id of the system default browser or of a browser of the list
    val choiceById = sequenceOf(systemDefault, listed, provided).flatten().find { it.id == choice?.id }
    defaultBrowserModel.selectedItem = choiceById ?: fallbackChoice()
  }

  /** Returns `null` when the system default is not available and the list has no active browser. */
  private fun fallbackChoice(): BrowserChoice? =
    if (canUseSystemDefaultBrowserPolicy()) BrowserChoice.systemDefault() else listedBrowsers().firstOrNull()

  /** The default browser that [WebBrowserManager] stores, as an item of the current dropdown. */
  private fun storedChoice(manager: WebBrowserManager): BrowserChoice? {
    val defaultBrowser = manager.defaultBrowser
    if (WebBrowserProvider.getAvailableBrowsers().any { it === defaultBrowser }) return BrowserChoice.of(defaultBrowser)
    return listedBrowsers().find { it.id == defaultBrowser.id } ?: fallbackChoice()
  }

  private val defaultBrowserChoice: BrowserChoice?
    get() = defaultBrowserModel.selected ?: fallbackChoice()

  override fun createComponent(): JComponent? {
    root = panel {
      val itemEditor: DialogItemEditor<ConfigurableWebBrowser> = object : DialogItemEditor<ConfigurableWebBrowser> {
        override fun getItemClass(): Class<ConfigurableWebBrowser> {
          return ConfigurableWebBrowser::class.java
        }

        override fun clone(item: ConfigurableWebBrowser, forInPlaceEditing: Boolean): ConfigurableWebBrowser {
          return ConfigurableWebBrowser(if (forInPlaceEditing) item.id else UUID.randomUUID(),
                                        item.family, item.name, item.path, item.isActive,
                                        if (forInPlaceEditing) item.specificSettings else cloneSettings(item))
        }

        override fun edit(
          browser: ConfigurableWebBrowser,
          mutator: Function<in ConfigurableWebBrowser, out ConfigurableWebBrowser>,
          isAdd: Boolean,
        ) {
          val settings = cloneSettings(browser)
          if (settings != null && ShowSettingsUtil.getInstance().editConfigurable(browsersTable, settings.createConfigurable())) {
            mutator.`fun`(browser).specificSettings = settings
          }
        }

        private fun cloneSettings(browser: ConfigurableWebBrowser): BrowserSpecificSettings? {
          val settings = browser.specificSettings ?: return null
          val newSettings = browser.family?.createBrowserSpecificSettings() ?: return null
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
            updateBrowserChoices()
          }
        })

      row {
        browsersTable = cell(browsersEditor.createComponent())
          .align(Align.FILL)
          .component
      }.resizableRow()

      row(IdeBundle.message("settings.browsers.default.browser")) {
        comboBox(defaultBrowserModel, browserChoiceRenderer())
      }

      group(IdeBundle.message("settings.browsers.show.browser.popup.in.the.editor")) {
        row {
          showBrowserHover = checkBox(IdeBundle.message("settings.browsers.show.browser.popup.html")).component
        }
        row {
          showBrowserHoverXml = checkBox(IdeBundle.message("settings.browsers.show.browser.popup.xml")).component
        }
      }

      group(IdeBundle.message("settings.browsers.reload.behavior")) {
        row(IdeBundle.message("setting.value.reload.mode.server")) {
          serverReloadModeComboBox = comboBox(ReloadMode.entries, textListCellRenderer("") { it.title })
            .component
        }
        row(IdeBundle.message("setting.value.reload.mode.preview")) {
          previewReloadModeComboBox = comboBox(ReloadMode.entries, textListCellRenderer("") { it.title })
            .component
        }
      }
    }
    return root
  }

  override fun isModified(): Boolean {
    val browserManager = WebBrowserManager.getInstance()
    if (defaultBrowserChoice != storedChoice(browserManager) ||
        browserManager.isShowBrowserHover != showBrowserHover.isSelected ||
        browserManager.isShowBrowserHoverXml != showBrowserHoverXml.isSelected ||
        browserManager.getWebPreviewReloadMode() !== previewReloadModeComboBox.item ||
        browserManager.getWebServerReloadMode() !== serverReloadModeComboBox.item) {
      return true
    }
    return browsersEditor.isModified
  }

  override fun apply() {
    val browserManager = WebBrowserManager.getInstance()
    val choice = defaultBrowserChoice
    browserManager.isShowBrowserHover = showBrowserHover.isSelected
    browserManager.isShowBrowserHoverXml = showBrowserHoverXml.isSelected
    browserManager.webPreviewReloadMode = previewReloadModeComboBox.item
    browserManager.webServerReloadMode = serverReloadModeComboBox.item
    browserManager.list = browsersEditor.apply()
    // the manager resolves the default browser in the new list
    browserManager.setDefaultBrowserId(choice?.id)
  }

  override fun reset() {
    val browserManager = WebBrowserManager.getInstance()
    previewReloadModeComboBox.item = browserManager.getWebPreviewReloadMode()
    serverReloadModeComboBox.item = browserManager.getWebServerReloadMode()
    showBrowserHover.isSelected = browserManager.isShowBrowserHover
    showBrowserHoverXml.isSelected = browserManager.isShowBrowserHoverXml
    browsersEditor.reset(browserManager.list)
    updateBrowserChoices()
    defaultBrowserModel.selectedItem = storedChoice(browserManager)
  }

  fun selectBrowser(browser: WebBrowser) {
    if (browser is ConfigurableWebBrowser) {
      browsersEditor.selectItem(browser)
    }
  }

  companion object {
    private val PATH_COLUMN_INFO: EditableColumnInfo<ConfigurableWebBrowser, String> = object : EditableColumnInfo<ConfigurableWebBrowser, String>(
      IdeBundle.message("settings.browsers.column.path")) {
      override fun valueOf(item: ConfigurableWebBrowser): String? {
        return PathUtil.toSystemDependentName(item.path)
      }

      override fun setValue(item: ConfigurableWebBrowser, value: String?) {
        item.path = value
      }

      override fun getEditor(item: ConfigurableWebBrowser): TableCellEditor? {
        return LocalPathCellEditor().fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()).normalizePath(true)
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

    private val FAMILY_RENDERER = object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        if (value is BrowserFamily) {
          icon = value.icon
          append(value.getName())
        }
        else {
          append(IdeBundle.message("settings.browsers.family.not.specified"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
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
      object : ColumnInfo<ConfigurableWebBrowser, BrowserFamily?>(
        IdeBundle.message("settings.browsers.column.family")) {
        override fun valueOf(item: ConfigurableWebBrowser): BrowserFamily? {
          return item.family
        }

        override fun setValue(item: ConfigurableWebBrowser, value: BrowserFamily?) {
          item.family = value
          item.specificSettings = value?.createBrowserSpecificSettings()
        }

        override fun getRenderer(item: ConfigurableWebBrowser): TableCellRenderer {
          return FAMILY_RENDERER
        }

        override fun getEditor(item: ConfigurableWebBrowser): TableCellEditor {
          val comboBox = ComboBox(CollectionComboBoxModel(listOf(null) + BrowserFamily.entries))
          comboBox.renderer = listCellRenderer(IdeBundle.message("settings.browsers.family.not.specified")) {
            icon(value.icon)
            text(value.getName())
          }
          return DefaultCellEditor(comboBox)
        }

        override fun isCellEditable(item: ConfigurableWebBrowser): Boolean {
          return !WebBrowserManager.getInstance().isPredefinedBrowser(item)
        }
      },
      PATH_COLUMN_INFO
    )

    private fun browserChoiceRenderer(): ListCellRenderer<BrowserChoice?> = listCellRenderer("") {
      icon(value.icon)
      text(value.name)
    }
  }
}
