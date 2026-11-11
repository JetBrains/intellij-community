// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.canUseSystemDefaultBrowserPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.CollapsibleRow
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.util.EnvironmentUtil
import com.intellij.util.Function
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
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
import kotlin.io.path.Path

/**
 * An item of the "Default Browser" dropdown: [SystemDefaultBrowser], a browser of a [WebBrowserProvider], a browser of the list,
 * or an installed browser that is not in the list yet.
 * The "JavaScript Debugger" dropdown has only the browsers of the [BrowserFamily.CHROME] family.
 * For a browser of the list, [id] is the id of the original row, see [BrowserSettingsConfigurable.originalOf].
 * For an installed browser, [installed] is what [detectInstalledBrowsers] found.
 * Apply adds a selected installed browser to the list, see [BrowserSettingsConfigurable.addInstalledBrowser].
 * The last item of each dropdown is [addItem]. It opens [BrowserDialog] and is never the selected item.
 * The [icon] takes part in the equality, so the page builds each [systemDefault] item with the same icon.
 */
internal data class BrowserChoice(val id: UUID, @NlsSafe val name: String, val icon: Icon, val installed: InstalledBrowser? = null) {
  val isAddItem: Boolean
    get() = id == ADD_ITEM_ID

  companion object {
    private val ADD_ITEM_ID: UUID = UUID.nameUUIDFromBytes("BrowserChoice.addItem".toByteArray())

    fun addItem(): BrowserChoice = BrowserChoice(ADD_ITEM_ID, IdeBundle.message("settings.browsers.add.browser"), AllIcons.General.Add)

    /** [icon] is the icon of the browser that the OS opens, see [SystemDefaultBrowserDetector]. */
    fun systemDefault(icon: Icon): BrowserChoice = BrowserChoice(SystemDefaultBrowser.id, SystemDefaultBrowser.name, icon)

    fun of(browser: WebBrowser): BrowserChoice = BrowserChoice(browser.id, browser.name, browser.icon)

    fun installed(browser: InstalledBrowser): BrowserChoice {
      val icon = ConfigurableWebBrowser(UUID.randomUUID(), browser.knownBrowser.family, browser.name, browser.path, true, null).icon
      return BrowserChoice(UUID.nameUUIDFromBytes(browser.path.toByteArray()), browser.name, icon, browser)
    }
  }
}

/**
 * The Web Browsers settings page.
 *
 * When the page opens, it detects the installed browsers and the OS default browser in the background.
 * It keeps the result while it is open.
 * The OS default item shows the icon of the OS default browser, see [SystemDefaultBrowserDetector].
 * The "Default Browser" dropdown offers each installed browser that no browser of the list opens, see [InstalledBrowser.isSameAs].
 * The "JavaScript Debugger" dropdown does the same for the Chromium-based browsers, see [WebBrowserManager.getChromiumBasedBrowser].
 *
 * The "Edit Browser…" icon button of a dropdown opens [BrowserDialog] for the selected browser of the list or installed browser.
 * The "Add Browser…" item of a dropdown opens [BrowserDialog] for a new browser.
 * The dialog changes only the table, and Apply stores the table as for the edits in the table.
 *
 * The sections of [WebBrowserSettingsSectionEP] come after the dropdowns.
 */
internal class BrowserSettingsConfigurable : UnnamedConfigurable {
  private lateinit var browsersTable: JComponent
  private lateinit var browsersEditor: TableModelEditor<ConfigurableWebBrowser>
  private var installedBrowsers: List<InstalledBrowser> = emptyList()

  /** The icon of each [BrowserChoice.systemDefault] item. The detection of the OS default browser sets it. */
  private var systemDefaultIcon: Icon = SystemDefaultBrowser.icon
  private val defaultBrowserModel = BrowserChoiceModel(isChromiumBased = false)
  private val chromiumBasedBrowserModel = BrowserChoiceModel(isChromiumBased = true)
  private lateinit var showBrowserHover: JBCheckBox
  private lateinit var showBrowserHoverXml: JBCheckBox
  private lateinit var installedBrowsersGroup: CollapsibleRow

  private var root: DialogPanel? = null

  /** The browser that [reset] selects in the table, see [selectBrowser]. */
  private var browserToSelect: ConfigurableWebBrowser? = null

  /** The sections of [WebBrowserSettingsSectionEP]. The page creates them with its component. */
  private var sections: List<UnnamedConfigurable> = emptyList()

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

  /** The dropdown item of a row of the table. */
  private fun choiceOf(row: ConfigurableWebBrowser): BrowserChoice = BrowserChoice(originalOf(row).id, row.name, row.icon)

  /** The active browsers of the table that have a path. */
  private fun listedBrowsers(): List<BrowserChoice> =
    browsersEditor.model.items
      .filter { it.isActive && it.path != null }
      .map(::choiceOf)

  /**
   * The browsers of the table of the [BrowserFamily.CHROME] family: the active browsers that have a path,
   * the stored browser and the predefined Chrome. The stored browser stays in the dropdown when it is inactive, as in [WebBrowserManager].
   */
  private fun listedChromiumBasedBrowsers(): List<BrowserChoice> {
    val storedId = WebBrowserManager.getInstance().chromiumBasedBrowserId
    return browsersEditor.model.items
      .filter { it.family == BrowserFamily.CHROME }
      .filter { row ->
        val id = originalOf(row).id
        row.isActive && row.path != null || id == storedId || id == WebBrowserManager.PREDEFINED_CHROME_ID
      }
      .map(::choiceOf)
  }

  /**
   * The installed browsers that no active browser of the table opens. The arguments of a browser of the table do not matter.
   * An inactive browser of the table does not hide an installed browser, see [addInstalledBrowser].
   */
  private fun installedChoices(): List<BrowserChoice> {
    val activeRows = browsersEditor.model.items.filter { it.isActive }
    return installedBrowsers.filter { installed -> activeRows.none(installed::isSameAs) }.map { BrowserChoice.installed(it) }
  }

  /** The installed browsers of [installedChoices] of the [BrowserFamily.CHROME] family. */
  private fun installedChromiumBasedChoices(): List<BrowserChoice> =
    installedChoices().filter { it.installed?.knownBrowser?.family == BrowserFamily.CHROME }

  /**
   * Makes [installed] a browser of the table, and returns its row.
   * It activates an inactive browser of the table that opens [installed]. Otherwise, it adds a browser to the table.
   */
  private fun addInstalledBrowser(installed: InstalledBrowser): ConfigurableWebBrowser {
    val model = browsersEditor.model
    val inactiveRow = model.items.firstOrNull { !it.isActive && installed.isSameAs(it) }
    val row = if (inactiveRow != null) {
      // the table edits a copy of the row, so Cancel keeps the browser of the settings inactive
      browsersEditor.getMutable(inactiveRow).also {
        it.isActive = true
        val index = model.indexOf(it)
        model.fireTableRowsUpdated(index, index)
      }
    }
    else {
      val family = installed.knownBrowser.family
      ConfigurableWebBrowser(UUID.randomUUID(), family, installed.name, installed.path, true, family.createBrowserSpecificSettings())
        .also { model.addRow(it) }
    }
    return row
  }

  /**
   * A model of a dropdown. A selection of [BrowserChoice.addItem] keeps the previous selection, and opens [addBrowser].
   * In the "JavaScript Debugger" dropdown, [isChromiumBased] is `true`, and the dialog keeps the [BrowserFamily.CHROME] family.
   */
  private inner class BrowserChoiceModel(val isChromiumBased: Boolean) : CollectionComboBoxModel<BrowserChoice>() {
    override fun setSelectedItem(item: Any?) {
      if (item is BrowserChoice && item.isAddItem) {
        // open the dialog after the popup of the dropdown closes
        ApplicationManager.getApplication().invokeLater({ addBrowser(this) }, ModalityState.stateForComponent(browsersTable)) { root == null }
      }
      else {
        super.setSelectedItem(item)
      }
    }
  }

  /** The row of the table for a browser of the list, or `null` for another item. */
  private fun rowOf(choice: BrowserChoice): ConfigurableWebBrowser? = browsersEditor.model.items.firstOrNull { choiceOf(it) == choice }

  /** Returns `true` when the "Edit Browser…" icon button can open [BrowserDialog] for [choice]. */
  private fun isEditable(choice: BrowserChoice?): Boolean = choice != null && (choice.installed != null || rowOf(choice) != null)

  /**
   * Opens [BrowserDialog] for the selected item of [model].
   * For an installed browser, OK adds the browser to the table, see [addInstalledBrowser].
   * A dropdown that selects the edited browser keeps it selected.
   */
  private fun editBrowser(model: BrowserChoiceModel) {
    val choice = model.selected ?: return
    val installed = choice.installed
    val row = if (installed == null) rowOf(choice) ?: return else null
    val browser = when {
      row != null -> ConfigurableWebBrowser(row.id, row.family, row.name, row.path, row.isActive, cloneSettings(row))
      else -> installed!!.knownBrowser.family.let { family ->
        ConfigurableWebBrowser(UUID.randomUUID(), family, installed.name, installed.path, true, family.createBrowserSpecificSettings())
      }
    }
    val isPredefined = row != null && WebBrowserManager.getInstance().isPredefinedBrowser(originalOf(row))
    if (!BrowserDialog(browsersTable, browser, isAdd = false, isFamilyEditable = !model.isChromiumBased && !isPredefined).showAndGet()) {
      return
    }
    val selectingModels = listOf(defaultBrowserModel, chromiumBasedBrowserModel).filter { it.selected == choice }
    val tableModel = browsersEditor.model
    // the table edits a copy of the row, so Cancel of the page keeps the browser of the settings
    val mutable = browsersEditor.getMutable(row ?: addInstalledBrowser(installed!!))
    mutable.name = browser.name
    mutable.path = browser.path
    mutable.family = browser.family
    mutable.specificSettings = browser.specificSettings
    val index = tableModel.indexOf(mutable)
    tableModel.fireTableRowsUpdated(index, index)
    val edited = choiceOf(mutable)
    for (selectingModel in selectingModels) {
      if (selectingModel.contains(edited)) {
        selectingModel.selectedItem = edited
      }
    }
  }

  /** Opens [BrowserDialog] for a new browser. OK adds the browser to the table and selects it in [model]. */
  private fun addBrowser(model: BrowserChoiceModel) {
    val family = if (model.isChromiumBased) BrowserFamily.CHROME else null
    val browser = ConfigurableWebBrowser(UUID.randomUUID(), family, "", null, true, family?.createBrowserSpecificSettings())
    if (!BrowserDialog(browsersTable, browser, isAdd = true, isFamilyEditable = !model.isChromiumBased).showAndGet()) {
      return
    }
    browsersEditor.model.addRow(browser)
    model.selectedItem = choiceOf(browser)
  }

  /**
   * Detects the installed browsers in the background, and adds them to the dropdown.
   * It also detects the OS default browser for the icon of the OS default item.
   * The page keeps the result while it is open.
   */
  private fun startDetection() {
    val modality = ModalityState.current()
    AppExecutorUtil.getAppExecutorService().execute {
      val knownBrowser = SystemDefaultBrowserDetector.getInstance().detectKnownBrowser()
      val detected = detectInstalledBrowsers(Path(SystemProperties.getUserHome()), EnvironmentUtil.getEnvironmentMap())
      ApplicationManager.getApplication().invokeLater({
        installedBrowsers = detected
        // the dropdowns get new items with the icon, and keep the selection by id
        systemDefaultIcon = knownBrowser?.let(SystemDefaultBrowserDetector::iconOf) ?: SystemDefaultBrowser.icon
        updateBrowserChoices()
      }, modality) { root == null }
    }
  }

  /** Fills the dropdowns from the table and keeps the selected items. */
  private fun updateBrowserChoices() {
    updateDefaultBrowserChoices()
    updateChromiumBasedBrowserChoices()
  }

  private fun updateDefaultBrowserChoices() {
    val choice = defaultBrowserModel.selected
    val systemDefault = if (canUseSystemDefaultBrowserPolicy()) listOf(BrowserChoice.systemDefault(systemDefaultIcon)) else emptyList()
    val provided = WebBrowserProvider.getAvailableBrowsers().map { BrowserChoice.of(it) }
    val listed = listedBrowsers()
    val installed = installedChoices()
    defaultBrowserModel.replaceAll(buildList {
      addAll(systemDefault)
      addAll(provided)
      addAll(listed)
      addAll(installed)
      add(BrowserChoice.addItem())
    })
    // the provided browsers come after the browsers of the list, so a provider cannot take the id of the system default browser
    // or of a browser of the list
    val choiceById = sequenceOf(systemDefault, listed, provided, installed).flatten().find { it.id == choice?.id }
    defaultBrowserModel.selectedItem = choiceById ?: fallbackChoice()
  }

  private fun updateChromiumBasedBrowserChoices() {
    val choice = chromiumBasedBrowserModel.selected
    val listed = listedChromiumBasedBrowsers()
    val choices = listed + installedChromiumBasedChoices()
    chromiumBasedBrowserModel.replaceAll(choices + BrowserChoice.addItem())
    chromiumBasedBrowserModel.selectedItem = choices.find { it.id == choice?.id } ?: chromeChoice(listed)
  }

  /** The predefined Chrome, as [WebBrowserManager.getChromiumBasedBrowser] falls back to it. */
  private fun chromeChoice(listed: List<BrowserChoice>): BrowserChoice? = listed.find { it.id == WebBrowserManager.PREDEFINED_CHROME_ID }

  /** The Chromium-based browser that [WebBrowserManager] stores, as an item of the current dropdown. */
  private fun storedChromiumBasedChoice(manager: WebBrowserManager): BrowserChoice? {
    val listed = listedChromiumBasedBrowsers()
    return listed.find { it.id == manager.chromiumBasedBrowser.id } ?: chromeChoice(listed)
  }

  private val chromiumBasedBrowserChoice: BrowserChoice?
    get() = chromiumBasedBrowserModel.selected ?: chromeChoice(listedChromiumBasedBrowsers())

  /** Returns `null` when the system default is not available and the list has no active browser. */
  private fun fallbackChoice(): BrowserChoice? =
    if (canUseSystemDefaultBrowserPolicy()) BrowserChoice.systemDefault(systemDefaultIcon) else listedBrowsers().firstOrNull()

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
            if (columnInfo === PATH_COLUMN_INFO || columnInfo === ACTIVE_COLUMN_INFO || columnInfo === FAMILY_COLUMN_INFO) {
              update()
            }
          }

          private fun update() {
            updateBrowserChoices()
          }
        })

      browserChoiceRow(IdeBundle.message("settings.browsers.default.browser"), defaultBrowserModel)
      browserChoiceRow(IdeBundle.message("settings.browsers.chromium.based.browser"), chromiumBasedBrowserModel)

      sections = ConfigurableWrapper.createConfigurables(WebBrowserSettingsSectionEP.EP_NAME)
      for (section in sections) {
        val component = section.createComponent() ?: continue
        row {
          cell(component).align(AlignX.FILL)
        }
      }

      group(IdeBundle.message("settings.browsers.editor")) {
        row(IdeBundle.message("settings.browsers.show.browser.popup.in")) {
          showBrowserHover = checkBox(IdeBundle.message("settings.browsers.show.browser.popup.html")).component
          showBrowserHoverXml = checkBox(IdeBundle.message("settings.browsers.show.browser.popup.xml")).component
        }
      }

      installedBrowsersGroup = collapsibleGroup(IdeBundle.message("settings.browsers.installed.browsers")) {
        row {
          comment(IdeBundle.message("settings.browsers.installed.browsers.comment"))
        }
        row {
          browsersTable = cell(browsersEditor.createComponent())
            .align(Align.FILL)
            .component
        }.resizableRow()
      }
      installedBrowsersGroup.resizableRow()
    }
    startDetection()
    return root
  }

  /** A dropdown of [model] with the "Edit Browser…" icon button, see [editBrowser]. */
  private fun Panel.browserChoiceRow(@NlsContexts.Label label: String, model: BrowserChoiceModel) {
    row(label) {
      val comboBox = comboBox(model, browserChoiceRenderer()).component
      val editAction = DumbAwareAction.create(IdeBundle.message("settings.browsers.edit.browser"), AllIcons.Actions.Edit) {
        editBrowser(model)
      }
      actionButton(editAction)
        .gap(RightGap.SMALL)
        .enabledIf(comboBox.selectedValueMatches(::isEditable))
    }
  }

  override fun disposeUIResources() {
    sections.forEach { it.disposeUIResources() }
    sections = emptyList()
    browserToSelect = null
    root = null
  }

  override fun isModified(): Boolean {
    val browserManager = WebBrowserManager.getInstance()
    if (defaultBrowserChoice != storedChoice(browserManager) ||
        chromiumBasedBrowserChoice != storedChromiumBasedChoice(browserManager) ||
        browserManager.isShowBrowserHover != showBrowserHover.isSelected ||
        browserManager.isShowBrowserHoverXml != showBrowserHoverXml.isSelected ||
        sections.any { it.isModified }) {
      return true
    }
    return browsersEditor.isModified
  }

  override fun apply() {
    val browserManager = WebBrowserManager.getInstance()
    // read both choices first, because an added installed browser changes the table, and the table refills the dropdowns
    val defaultChoice = defaultBrowserChoice
    val chromiumBasedChoice = chromiumBasedBrowserChoice
    // read the ids before the table applies its edits, because then the table replaces an edited copy with its original row.
    // both dropdowns can select one installed browser, and the table must get it only once
    val addedIds = HashMap<InstalledBrowser, UUID>()
    fun idOf(choice: BrowserChoice?): UUID? = choice?.installed?.let { addedIds.getOrPut(it) { originalOf(addInstalledBrowser(it)).id } } ?: choice?.id
    val defaultBrowserId = idOf(defaultChoice)
    val chromiumBasedBrowserId = idOf(chromiumBasedChoice)
    browserManager.isShowBrowserHover = showBrowserHover.isSelected
    browserManager.isShowBrowserHoverXml = showBrowserHoverXml.isSelected
    browserManager.list = browsersEditor.apply()
    // the manager resolves the default browser in the new list
    browserManager.setDefaultBrowserId(defaultBrowserId)
    browserManager.chromiumBasedBrowserId = chromiumBasedBrowserId ?: WebBrowserManager.PREDEFINED_CHROME_ID
    // an added installed browser is a browser of the list now
    defaultBrowserModel.selectedItem = storedChoice(browserManager)
    updateChromiumBasedBrowserChoices()
    chromiumBasedBrowserModel.selectedItem = storedChromiumBasedChoice(browserManager)
    sections.filter { it.isModified }.forEach { it.apply() }
  }

  override fun reset() {
    val browserManager = WebBrowserManager.getInstance()
    showBrowserHover.isSelected = browserManager.isShowBrowserHover
    showBrowserHoverXml.isSelected = browserManager.isShowBrowserHoverXml
    browsersEditor.reset(browserManager.list)
    updateBrowserChoices()
    defaultBrowserModel.selectedItem = storedChoice(browserManager)
    chromiumBasedBrowserModel.selectedItem = storedChromiumBasedChoice(browserManager)
    sections.forEach { it.reset() }
    selectBrowserToSelect()
  }

  /**
   * Expands the "Installed Browsers" group and selects [browser] in the table.
   * The settings dialog resets the page after it creates the component, and the reset of the table clears its selection.
   * So the page keeps [browser] and selects it again on each [reset].
   */
  fun selectBrowser(browser: WebBrowser) {
    if (browser is ConfigurableWebBrowser) {
      browserToSelect = browser
      if (root != null) {
        selectBrowserToSelect()
      }
    }
  }

  private fun selectBrowserToSelect() {
    val browser = browserToSelect ?: return
    val row = browsersEditor.model.items.firstOrNull { originalOf(it).id == browser.id } ?: return
    installedBrowsersGroup.expanded = true
    browsersEditor.selectItem(originalOf(row))
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

    private val FAMILY_COLUMN_INFO: ColumnInfo<ConfigurableWebBrowser, BrowserFamily?> = object : ColumnInfo<ConfigurableWebBrowser, BrowserFamily?>(
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
        comboBox.renderer = browserFamilyRenderer()
        return DefaultCellEditor(comboBox)
      }

      override fun isCellEditable(item: ConfigurableWebBrowser): Boolean {
        return !WebBrowserManager.getInstance().isPredefinedBrowser(item)
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
      FAMILY_COLUMN_INFO,
      PATH_COLUMN_INFO
    )

    private fun browserChoiceRenderer(): ListCellRenderer<BrowserChoice?> = listCellRenderer("") {
      if (value.isAddItem) {
        separator {}
      }
      icon(value.icon)
      text(value.name)
    }

    private fun cloneSettings(browser: ConfigurableWebBrowser): BrowserSpecificSettings? {
      val settings = browser.specificSettings ?: return null
      val newSettings = browser.family?.createBrowserSpecificSettings() ?: return null
      TableModelEditor.cloneUsingXmlSerialization(settings, newSettings)
      return newSettings
    }
  }
}
