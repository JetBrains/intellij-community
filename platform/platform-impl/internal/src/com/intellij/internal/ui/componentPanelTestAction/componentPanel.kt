// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentPanelTestAction

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.cellvalidators.CellComponentProvider
import com.intellij.openapi.ui.cellvalidators.CellTooltipManager
import com.intellij.openapi.ui.cellvalidators.StatefulValidatingCellEditor
import com.intellij.openapi.ui.cellvalidators.ValidatingTableCellRendererWrapper
import com.intellij.openapi.ui.cellvalidators.ValidationUtils
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.GotItTooltip
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.net.MalformedURLException
import java.net.URL
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

private val ALLOWED_VALUES = setOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
                                   "thirteen", "fourteen", "fifteen", "sixteen", "abracadabra")

private val WARNING_CELL_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, null)

private val NULL_VALUE_ERROR = ValidationInfo("Null value")
private val NAN_VALUE_ERROR = ValidationInfo("Not a number")

private const val LONG_TEXT2 = "It is not the way of the Wild to like movement.<p/>Life is an offence to it, for life is movement; and the Wild aims always to destroy movement."

private const val GOT_IT_HEADER = "IDE features trainer"
private const val GOT_IT_TEXT = "Learn the most useful shortcuts <icon src=\"AllIcons.Actions.More\"/> and essential IDE features interactively." +
                                " Use <icon src=\"AllIcons.Actions.Diff\" valign=\"1.0f\"/> for details."
private const val GOT_IT_TEXT2 = "Some textfield that actually means nothing"

internal fun createComponentPanel(project: Project, disposable: Disposable, tabbedPane: JTabbedPane): DialogPanel {
  val text1 = JTextField()
  ComponentValidator(disposable)
    .withHyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        println("Text1 link clicked. Desc = ${e.description}")
      }
    }
    .withValidator {
      val tt = text1.text
      if (!tt.isNullOrEmpty()) {
        try {
          tt.toInt()
          null
        }
        catch (_: NumberFormatException) {
          ValidationInfo("Warning, expecting a number.<br/>Visit the <a href=\"#link.one\">information link</a>" +
                         "<br/>Or <a href=\"#link.two\">another link</a>", text1).asWarning()
        }
      }
      else {
        null
      }
    }
    .withFocusValidator {
      val tt = text1.text
      if (!tt.isNullOrEmpty()) {
        try {
          val i = tt.toInt()
          if (i == 555) ValidationInfo("Wrong number", text1).asWarning() else null
        }
        catch (_: NumberFormatException) {
          ValidationInfo("Warning, expecting a number.", text1).asWarning()
        }
      }
      else {
        null
      }
    }
    .andRegisterOnDocumentListener(text1)
    .installOn(text1)

  val d = text1.preferredSize
  text1.preferredSize = Dimension(JBUIScale.scale(100), d.height)

  val text2 = JTextField()
  ComponentValidator(disposable)
    .withHyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        println("Text2 link clicked. Desc = ${e.description}")
      }
    }
    .withValidator {
      val tt = text2.text
      when {
        tt.isNullOrEmpty() || tt.length < 5 ->
          ValidationInfo("'$tt': message is too short.<br/>Should contain at least 5 symbols. 8 is preferred <a href=\"#check.rules\">check rules.</a>", text2)
        tt.length < 8 ->
          ValidationInfo("'$tt': message of 8 symbols is preferred", text2).asWarning()
        else ->
          null
      }
    }
    .andStartOnFocusLost()
    .andRegisterOnDocumentListener(text2)
    .installOn(text2)

  val abracadabraButton = JButton("Abracadabra")
  HelpTooltip().setDescription(LONG_TEXT2).installOn(abracadabraButton)

  try {
    GotItTooltip("Abracadabda.button", GOT_IT_TEXT, project)
      .withShowCount(3)
      .withHeader(GOT_IT_HEADER)
      .withIcon(AllIcons.General.BalloonInformation)
      .withBrowserLink("Learn more", URL("https://www.jetbrains.com/"))
      .show(abracadabraButton, GotItTooltip.BOTTOM_MIDDLE)

    GotItTooltip("textfield", GOT_IT_TEXT2, project).withShowCount(5).show(text1, GotItTooltip.BOTTOM_MIDDLE)
  }
  catch (_: MalformedURLException) {
  }

  val scrollPane = JBScrollPane(createTable(disposable))
  scrollPane.preferredSize = JBUI.size(400, 300)
  scrollPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL)

  return panel {
    row {
      checkBox("Scroll tab layout")
        .comment("Set tabbed pane tabs layout property to SCROLL_TAB_LAYOUT")
        .onChanged {
          tabbedPane.tabLayoutPolicy = if (it.isSelected) JTabbedPane.SCROLL_TAB_LAYOUT else JTabbedPane.WRAP_TAB_LAYOUT
        }
    }
    row {
      checkBox("Full border")
        .applyToComponent { setToolTipText(HtmlChunk.text("Enable full border around the tabbed pane")) }
        .onChanged {
          tabbedPane.putClientProperty("JTabbedPane.hasFullBorder", it.isSelected)
        }
    }
    row {
      cell(abracadabraButton)
    }
    row {
      cell(scrollPane)
        .label("Table label:", LabelPosition.TOP)
        .align(Align.FILL)
        .comment("&lt;Project&gt; is content roots of all modules, all immediate descendants<br/>of the projects base directory, and .idea directory contents")
    }.resizableRow()
  }
}

private fun createTable(disposable: Disposable): JBTable {
  val columns = arrayOf("First column", "Second column")
  val data = arrayOf(
    arrayOf("one", "1"), arrayOf("two", "2"), arrayOf("three", "3"), arrayOf("four", "4"), arrayOf("five", "5"),
    arrayOf("six", "6"), arrayOf("seven", "7"), arrayOf("eight", "8"), arrayOf("nine", "9"), arrayOf("ten", "10"),
    arrayOf("eleven", "11"), arrayOf("twelve", "12"), arrayOf("thirteen", "13"), arrayOf("fourteen", "14"),
    arrayOf("fifteen", "15"), arrayOf("sixteen", "16")
  )

  val table = JBTable(object : DefaultTableModel() {
    override fun getColumnName(column: Int): String = columns[column]
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, col: Int): Any = if (col == 0) data[row][col] else data[row][col].toInt()

    override fun setValueAt(value: Any, row: Int, col: Int) {
      if (col == 0 || col == 1) {
        data[row][col] = value.toString()
        fireTableCellUpdated(row, col)
      }
    }
  })

  val hyperlinkListener = HyperlinkListener { e ->
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      println("Table cell tooltip link clicked. Desc = ${e.description}")
    }
  }

  // Install custom tooltip manager for displaying error/warning tooltips
  CellTooltipManager(disposable)
    .withCellComponentProvider(CellComponentProvider.forTable(table))
    .withHyperlinkListener(hyperlinkListener)
    .installOn(table)

  // Configure left column
  val cellEditor = ExtendableTextField()
  val browseExtension = ExtendableTextComponent.Extension.create(
    AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover, "Open file", true
  ) { println("Table browse clicked") }
  cellEditor.addExtension(browseExtension)
  cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)

  ComponentValidator(disposable)
    .withValidator {
      val isAllowed = ALLOWED_VALUES.contains(cellEditor.text)
      ValidationUtils.setExtension(cellEditor, ValidationUtils.ERROR_EXTENSION, !isAllowed)
      if (isAllowed) null else ValidationInfo("Illegal value: ${cellEditor.text} <a href=\"#check.cell.rules\">check rules.</a>", cellEditor)
    }
    .withHyperlinkListener(hyperlinkListener)
    .andRegisterOnDocumentListener(cellEditor)
    .installOn(cellEditor)

  var col = table.columnModel.getColumn(0)
  col.cellEditor = DefaultCellEditor(cellEditor)
  col.cellRenderer = ValidatingTableCellRendererWrapper(DefaultTableCellRenderer())
    .bindToEditorSize { cellEditor.preferredSize }
    .withCellValidator { value, _, _ ->
      when {
        value == null -> ValidationInfo("Null value")
        ALLOWED_VALUES.contains(value.toString()) -> null
        else -> ValidationInfo("Illegal value: $value <a href=\"#check.cell.rules\">check rules.</a>", null)
      }
    }

  // Configure right column
  val rightEditor = ComboBox(data.map { it[1].toInt() }.toTypedArray())
  col = table.columnModel.getColumn(1)

  col.cellEditor = StatefulValidatingCellEditor(rightEditor, disposable)
  col.cellRenderer = ValidatingTableCellRendererWrapper(object : ColoredTableCellRenderer() {
    init {
      ipad = JBInsets.emptyInsets() // Reset standard pads
    }

    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value == null) {
        append("No data", SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
      else {
        try {
          val iv = value.toString().toInt()
          append("value ", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
          append(value.toString(), if (iv <= 8) SimpleTextAttributes.REGULAR_ATTRIBUTES else WARNING_CELL_ATTRIBUTES)
        }
        catch (_: NumberFormatException) {
          append(value.toString(), SimpleTextAttributes.ERROR_ATTRIBUTES)
        }
      }
    }
  })
    .bindToEditorSize { rightEditor.preferredSize }
    .withCellValidator { value, _, _ ->
      if (value == null) {
        NULL_VALUE_ERROR
      }
      else {
        try {
          val iv = value.toString().toInt()
          if (iv <= 8) null else ValidationInfo("Value $value is not preferred").asWarning()
        }
        catch (_: NumberFormatException) {
          NAN_VALUE_ERROR
        }
      }
    }

  return table
}
