// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections

import com.intellij.codeInsight.AnnotationPanelModel
import com.intellij.core.JavaPsiBundle
import com.intellij.ide.setToolTipText
import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.AnActionButtonUpdater
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBDimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.concurrent.Callable
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

class NullableAnnotationsPanel(
  private val myProject: Project,
  model: AnnotationPanelModel,
  showInstrumentationOptions: Boolean,
) {
  private val myDefaultAnnotations = HashSet<String>(model.getDefaultAnnotations())
  private val myTable: JBTable?
  private val myComponent: JPanel
  private val myCombo: ComboBox<String>
  private val myTableModel: DefaultTableModel

  init {
    val annotations = model.getAnnotations()
    myCombo = ComboBox<String>(annotations.sorted().toTypedArray())
    val defaultAnnotation = model.getDefaultAnnotation()
    if (!annotations.contains(defaultAnnotation)) {
      addAnnotationToCombo(defaultAnnotation)
    }
    if (model.hasAdvancedAnnotations()) {
      loadAdvancedAnnotations(model)
    }
    myCombo.selectedItem = defaultAnnotation

    myTableModel = object : DefaultTableModel() {
      override fun isCellEditable(row: Int, column: Int) = column == 1
    }
    myTableModel.columnCount = if (showInstrumentationOptions) 2 else 1
    for (annotation in annotations) {
      addRow(annotation, model.getCheckedAnnotations().contains(annotation))
    }

    val columnModel = DefaultTableColumnModel()
    columnModel.addColumn(TableColumn(0, 100, object : ColoredTableCellRenderer() {
      override fun acquireState(table: JTable?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        super.acquireState(table, isSelected, false, row, column)
      }

      override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
      ) {
        append(value as String, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }, null))

    myTable = JBTable(myTableModel, columnModel)
    if (!showInstrumentationOptions) myTable.setTableHeader(null)

    if (showInstrumentationOptions) {
      columnModel.getColumn(0).setHeaderValue(JavaPsiBundle.message("node.annotation.tooltip"))

      val checkColumn = TableColumn(1, 100, BooleanTableCellRenderer(), BooleanTableCellEditor())
      columnModel.addColumn(checkColumn)
      checkColumn.setHeaderValue(" " + JavaBundle.message("nullable.notnull.annotations.panel.column.instrument") + " ")

      val headerRenderer = createHeaderRenderer()
      myTable.getTableHeader().defaultRenderer = headerRenderer
      checkColumn.setHeaderRenderer(headerRenderer)
      checkColumn.sizeWidthToFit()
    }

    val toolbarDecorator = ToolbarDecorator.createDecorator(myTable)
      .setMoveUpAction(object : AnActionButtonRunnable {
        override fun run(button: AnActionButton?) {
          val selectedRow = myTable.selectedRow
          if (selectedRow < 1) return
          val vector: MutableList<Any?> = myTableModel.getDataVector()[selectedRow]
          myTableModel.removeRow(selectedRow)
          myTableModel.insertRow(selectedRow - 1, vector.toTypedArray())
          myTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1)
        }
      })
      .setMoveDownAction(object : AnActionButtonRunnable {
        override fun run(button: AnActionButton?) {
          val selectedRow = myTable.selectedRow
          if (selectedRow < 0 || selectedRow >= myTableModel.rowCount - 1) return
          val vector: MutableList<Any?> = myTableModel.getDataVector()[selectedRow]
          myTableModel.removeRow(selectedRow)
          myTableModel.insertRow(selectedRow + 1, vector.toTypedArray())
          myTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1)
        }
      })
      .setAddAction(AnActionButtonRunnable { chooseAnnotation(model.getName()) })
      .setRemoveAction(object : AnActionButtonRunnable {
        override fun run(anActionButton: AnActionButton?) {
          val selectedValue: String? = selectedAnnotation
          if (selectedValue == null) return
          myCombo.removeItem(selectedValue)

          var rowIndex = -1
          for (i in myTableModel.getDataVector().indices) {
            if (myTableModel.getDataVector()[i].contains(selectedValue)) {
              rowIndex = i
              break
            }
          }
          if (rowIndex != -1) myTableModel.removeRow(rowIndex)
        }
      })
      .setRemoveActionUpdater(AnActionButtonUpdater { !myDefaultAnnotations.contains(this.selectedAnnotation) })

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    myTable.setRowSelectionAllowed(true)
    myTable.setShowGrid(false)

    myComponent = panel {
      row {
        cell(toolbarDecorator.createPanel())
          .align(Align.FILL)
          .label(JavaBundle.message("nullable.notnull.annotations.panel.title", model.getName()), LabelPosition.TOP)
          .comment(JavaBundle.message("nullable.notnull.annotations.panel.description"))
          .resizableColumn()
          .applyToComponent {
            preferredSize = JBDimension(preferredSize.width, 200)
          }
      }
        .resizableRow()
      row(JavaBundle.message("nullable.notnull.annotation.used.label")) {
        cell(myCombo)
        contextHelp(JavaBundle.message("nullable.notnull.annotation.used.label.description"))
      }
    }
  }

  private fun createHeaderRenderer(): TableCellRenderer {
    val defaultRenderer = myTable!!.getTableHeader().defaultRenderer

    val headerRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
      val component = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      if (component is JComponent) {
        component.setToolTipText(
          if (column == 1) HtmlChunk.text(JavaBundle.message("nullable.notnull.annotations.runtime.instrumentation.tooltip")) 
          else HtmlChunk.empty())
      }
      component
    }
    return headerRenderer
  }

  private fun loadAdvancedAnnotations(model: AnnotationPanelModel) {
    // No project-specific annotations are possible for default project
    if (myProject.isDefault) return
    val loading = JavaBundle.message("loading.additional.annotations")
    myCombo.addItem(loading)
    DumbService.getInstance(myProject).runWhenSmart(Runnable {
      ReadAction.nonBlocking(Callable<List<String>> { model.getAdvancedAnnotations() })
        .finishOnUiThread(ModalityState.any()) { advancedAnnotations ->
          myCombo.removeItem(loading)
          val count = myCombo.itemCount
          val selectedItem = myCombo.selectedItem
          val newItems: List<@NlsSafe String> =
            ((0 until count).map { myCombo.getItemAt(it) } + advancedAnnotations).distinct()
          myCombo.removeAllItems()
          newItems.forEach { myCombo.addItem(it) }
          myCombo.selectedItem = selectedItem
        }.submit(AppExecutorUtil.getAppExecutorService())
    })
    myCombo.addActionListener(object : ActionListener {
      var previous: Any? = myCombo.selectedItem

      override fun actionPerformed(e: ActionEvent?) {
        val item = myCombo.selectedItem
        if (item === loading) {
          myCombo.setSelectedItem(previous)
        }
        else {
          previous = item
        }
      }
    })
  }

  private fun addRow(annotation: String?, checked: Boolean) {
    val row = myTable?.selectedRow ?: -1
    if (row == -1) {
      myTableModel.addRow(arrayOf<Any?>(annotation, checked))
    }
    else {
      myTableModel.insertRow(row + 1, arrayOf<Any?>(annotation, checked))
    }
  }

  private fun selectAnnotation(annotation: String): Int? {
    for (i in 0..<myTableModel.rowCount) {
      if (annotation == myTableModel.getValueAt(i, 0)) {
        myTable!!.setRowSelectionInterval(i, i)
        myTable.scrollRectToVisible(myTable.getCellRect(i, 0, true))
        return i
      }
    }
    return null
  }

  @get:NlsSafe
  private val selectedAnnotation: @NlsSafe String?
    get() {
      val selectedRow = myTable!!.selectedRow
      return if (selectedRow < 0) null else myTableModel.getValueAt(selectedRow, 0) as String?
    }

  private fun chooseAnnotation(@NlsSafe title: @NlsSafe String) {
    val chooser = TreeClassChooserFactory.getInstance(myProject)
      .createNoInnerClassesScopeChooser(
        JavaBundle.message("dialog.title.choose.annotation", title),
        GlobalSearchScope.allScope(myProject),
        ClassFilter { obj: PsiClass? -> obj!!.isAnnotationType() },
        null
      )
    chooser.showDialog()
    val selected = chooser.getSelected()
    if (selected == null) {
      return
    }
    val qualifiedName = selected.getQualifiedName()
    if (selectAnnotation(qualifiedName!!) == null) {
      addRow(qualifiedName, false)
      addAnnotationToCombo(qualifiedName)
      checkNotNull(selectAnnotation(qualifiedName))
      myTable!!.requestFocus()
    }
  }

  private fun addAnnotationToCombo(@NlsSafe annotation: @NlsSafe String) {
    var insertAt = 0
    while (insertAt < myCombo.itemCount) {
      if (myCombo.getItemAt(insertAt) >= annotation) break
      insertAt += 1
    }
    myCombo.insertItemAt(annotation, insertAt)
  }

  val component: JComponent
    get() = myComponent

  val defaultAnnotation: String?
    get() = myCombo.getItem()

  val annotations: Array<String?>
    get() {
      val size = myTableModel.rowCount
      val result = arrayOfNulls<String>(size)
      for (i in 0..<size) {
        result[i] = myTableModel.getValueAt(i, 0) as String?
      }
      return result
    }

  val checkedAnnotations: List<String?>
    get() = (0..<myTableModel.rowCount)
      .asSequence()
      .filter { myTableModel.getValueAt(it, 1) == true }
      .map { myTableModel.getValueAt(it, 0) as String? }
      .toList()
}