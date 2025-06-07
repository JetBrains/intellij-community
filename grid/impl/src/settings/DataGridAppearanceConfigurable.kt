package com.intellij.database.settings

import com.intellij.database.DataGridBundle
import com.intellij.database.csv.CsvFormats
import com.intellij.database.csv.CsvFormatter.setFirstRowIsHeader
import com.intellij.database.csv.ui.preview.TableAndTextCsvFormatPreview
import com.intellij.database.datagrid.*
import com.intellij.database.datagrid.GridUtil.configureCsvTable
import com.intellij.database.datagrid.GridUtil.createPreviewDataGrid
import com.intellij.database.run.ui.DataGridRequestPlace
import com.intellij.database.run.ui.grid.GridColorsScheme
import com.intellij.database.settings.DataGridAppearanceSettings.BooleanMode
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector.Once
import java.awt.event.ItemListener

class DataGridAppearanceConfigurable : BoundSearchableConfigurable(
  IdeBundle.message("configurable.DatabaseSettingsConfigurable.DataViews.display.name"), HelpID.DATA_VIEWS_APPEARANCE_SETTINGS,
  ID) {

  private var isResetting = false

  private lateinit var components: AppearanceComponents
  private lateinit var grid: DataGrid
  private val useCustomFont: Boolean
    get() = components.useCustomFontCell.component.isSelected

  companion object {
    const val ID: String = "database.data.views.appearance"
  }

  override fun apply() {
    super.apply()
    components.customFontOptionsPanel.apply(useCustomFont)
    DataGridAppearanceSettingsImpl.fireSettingsChanged()
  }

  override fun isModified(): Boolean {
    return super.isModified() || components.customFontOptionsPanel.isModified(useCustomFont)
  }

  override fun reset() {
    try {
      isResetting = true
      super.reset()
      components.customFontOptionsPanel.reset()
      updateGrid()
    }
    finally {
      isResetting = false
    }
  }

  private fun updateGrid() {
    val striped = components.isStripeRowsCheckBox.component.isSelected
    grid.appearance.setResultViewStriped(striped)
    grid.appearance.booleanMode = components.booleanMode.component.item
    grid.appearance.setResultViewSetShowHorizontalLines(!striped)
    grid.colorsScheme.updateFromScheme(useCustomFont, components.customFontOptionsPanel.scheme)
    grid.resultView.reinitSettings()
    grid.panel.component.revalidate()
    grid.panel.component.repaint()
  }

  override fun createPanel(): DialogPanel {
    val settings = DataGridAppearanceSettings.getSettings()
    val panel = panel {
      components = produceAppearanceSettings(settings, { isResetting }, { updateGrid() })
      row {
        val project = DefaultProjectFactory.getInstance().defaultProject
        val format = setFirstRowIsHeader(CsvFormats.CSV_FORMAT.value, true)
        val document = EditorFactory.getInstance().createDocument(TableAndTextCsvFormatPreview.formatData(format))
        val hookUp = CsvDocumentDataHookUp(project, format, document, null)
        val disposable = disposable!!
        Disposer.register(disposable, hookUp)
        grid = createPreviewDataGrid(project, hookUp, configureCsvTable().andThen { grid, appearance ->
          appearance.setResultViewVisibleRowCount(8)
          grid.panel.component.border = JBUI.Borders.customLine(JBColor.border(), 1)
        })
        grid.presentationMode = GridPresentationMode.TABLE
        Disposer.register(disposable, grid)
        Disposer.register(disposable, Once.installOn(grid.panel.component, object : Activatable {
          override fun showNotify() {
            val source = GridRequestSource(DataGridRequestPlace(grid))
            hookUp.loader.loadFirstPage(source)
          }
        }))
        cell(grid.panel.component).align(Align.FILL)
      }.topGap(TopGap.MEDIUM)
    }
    return panel
  }
}

class AppearanceComponents internal constructor(
  val useCustomFontCell: Cell<JBCheckBox>,
  val customFontOptionsPanel: DataGridFontOptionsPanel,
  val isStripeRowsCheckBox: Cell<JBCheckBox>,
  val booleanMode: Cell<ComboBox<BooleanMode>>,
)

fun Panel.produceAppearanceSettings(settings: DataGridAppearanceSettings,
                                    isResetting: () -> Boolean,
                                    updateCallback: () -> Unit = { }): AppearanceComponents {
  var useCustomFontCell: Cell<JBCheckBox>? = null
  var customFontOptionsPanel: DataGridFontOptionsPanel? = null
  var isStripeRowsCheckBox: Cell<JBCheckBox>? = null
  var booleanMode: Cell<ComboBox<BooleanMode>>? = null

  row {
    useCustomFontCell = checkBox(IdeBundle.message("checkbox.override.default.laf.fonts"))
      .bindSelected(settings::getUseGridCustomFont, settings::setUseGridCustomFont)
      .onChanged { updateCallback() }
      .applyToComponent {
        addItemListener(ItemListener {
          if (!isResetting()) {
            val useCustomFont = useCustomFontCell?.component?.isSelected ?: return@ItemListener
            customFontOptionsPanel?.apply {
              setDelegatingPreferencesImpl(!useCustomFont)
            }
          }
        })
      }
  }

  indent {
    row {
      customFontOptionsPanel = DataGridFontOptionsPanel(GridColorsScheme(false, settings), updateCallback).also {
        cell(it.content)
      }
    }.bottomGap(BottomGap.SMALL)
  }
  row {
    isStripeRowsCheckBox = checkBox(DataGridBundle.message("settings.stripped.tables"))
      .bindSelected(settings::isStripedTable, settings::setStripedTable)
      .onChanged { updateCallback() }
  }
  row(DataGridBundle.message("settings.show.boolean.values.as")) {
    booleanMode = comboBox(listOf(BooleanMode.TEXT, BooleanMode.CHECKBOX), listCellRenderer {
      val text =
        when (value) {
          BooleanMode.TEXT -> DataGridBundle.message("boolean.values.mode.text")
          BooleanMode.CHECKBOX -> DataGridBundle.message("boolean.values.mode.checkbox")
          null -> ""
        }
      text(text)
    }).bindItem(settings::getBooleanMode) { if (it != null) settings.booleanMode = it }
      .onChanged { updateCallback() }
  }

  return AppearanceComponents(useCustomFontCell!!, customFontOptionsPanel!!, isStripeRowsCheckBox!!, booleanMode!!)
}
