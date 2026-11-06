package com.intellij.database.csv.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.csv.CsvFormat
import com.intellij.database.csv.ui.preview.CsvFormatPreview
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.view.editors.DataGridEditorUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

@Suppress("LeakingThis")
abstract class CsvFormatsUI(allowNameEditing: Boolean, settings: CsvFormatUISettings) : Disposable {

  private val myFormatsList = CsvFormatsListComponent(this)

  private val myPanel: JComponent

  private val myFormatForm = CsvFormatForm(this, settings)

  private var myPreview: CsvFormatPreview? = null

  init {
    FormatsListToFormatEditorBond().setListeners()

    val decorator = createFormatListDecorator()
    val formatForm = myFormatForm.component
    formatForm.border = JBUI.Borders.emptyRight(JBUI.unscale(UIUtil.getScrollBarWidth()))
    val formatFormScrollPane = ScrollPaneFactory.createScrollPane(
      formatForm, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
      true) as JBScrollPane
    formatFormScrollPane.isOverlappingScrollBar = true

    myPanel = panel {
      row {
        cell(DataGridEditorUtil.labeledDecorator(JBLabel(DataGridBundle.message("csv.format.settings.formats")), decorator))
          .align(AlignX.FILL)
          .customize(UnscaledGaps(bottom = 8, right = JBUI.unscale(UIUtil.getScrollBarWidth())))
      }
      row {
        cell(formatFormScrollPane)
          .align(Align.FILL)
      }.resizableRow()
    }

    ToolbarDecorator.findEditButton(decorator.actionsPanel)?.isVisible = false

    myFormatsList.setNameEditingAllowed(allowNameEditing)
    myFormatsList.addChangeListener {
      val format = selectedFormat ?: return@addChangeListener
      myPreview?.setFormat(format, GridRequestSource(null))
    }

    formatFormScrollPane.preferredSize = formatFormScrollPane.viewport.view.preferredSize
  }

  val formatForm: CsvFormatForm
    get() = myFormatForm

  fun reset(formats: List<CsvFormat>, nameToSelect: String?) {
    myFormatsList.reset(formats, nameToSelect)
  }

  fun select(format: CsvFormat?): CsvFormat? {
    val formats = this.formats.toList()
    val existing = findFormat(format)
    if (format != null && existing == null) {
      return myFormatsList.newFormat(format)
    }
    myFormatsList.reset(formats, existing?.name)
    return existing
  }

  val formats: List<CsvFormat>
    get() = myFormatsList.formats

  val selectedFormat: CsvFormat?
    get() = myFormatsList.selected

  fun attachPreview(preview: CsvFormatPreview) {
    myPreview = preview
  }

  val component: JComponent
    get() = myPanel

  override fun dispose() {
  }

  val formatsListComponent: CsvFormatsListComponent
    get() = myFormatsList

  protected abstract fun createFormatListDecorator(): ToolbarDecorator

  private fun findFormat(format: CsvFormat?): CsvFormat? {
    if (format == null) return null
    return formats.firstOrNull { csvFormat ->
      csvFormat == CsvFormat(csvFormat.name, format.dataRecord, format.headerRecord, csvFormat.id, format.rowNumbers)
    }
  }

  private inner class FormatsListToFormatEditorBond : CsvFormatsListComponent.ChangeListener, CsvFormatForm.ChangeListener {
    private var myUpdating = false

    fun setListeners() {
      myFormatsList.addChangeListener(this)
      myFormatForm.addChangeListener(this)
    }

    override fun formatsChanged(formatsListComponent: CsvFormatsListComponent) {
      update {
        val selectedFormat = myFormatsList.selected
        if (selectedFormat == null) {
          myFormatForm.component.isVisible = false
        }
        else {
          myFormatForm.component.isVisible = true
          myFormatForm.reset(selectedFormat)
        }
      }
    }

    override fun formatChanged(source: CsvFormatForm) {
      update {
        myFormatsList.updateSelectedFormat(myFormatForm.format)
      }
    }

    private fun update(updater: () -> Unit) {
      if (myUpdating) return

      myUpdating = true
      try {
        updater()
      }
      finally {
        myUpdating = false
      }
    }
  }
}
