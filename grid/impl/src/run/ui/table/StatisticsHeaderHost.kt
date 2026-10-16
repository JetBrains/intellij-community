package com.intellij.database.run.ui.table

import com.intellij.database.run.ui.table.statisticsPanel.StatisticsTableHeader
import com.intellij.ui.table.AdditionalTableHeader
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JTable

/** Connects a statistics component to Swing without exposing the local table. */
internal class StatisticsHeaderHost(
  table: GridTable,
  private val view: TableResultView,
  private val statistics: StatisticsTableHeader,
) : AdditionalTableHeader() {
  override var table: JTable? = null

  init {
    isOpaque = false
    position = statistics.position
    installTable(table)
  }

  override fun detachController() {
    columnsController?.detach()
    statistics.installView(null)
  }

  override fun recreateController() {
    statistics.installView(view)
    super.recreateController()
  }

  override fun createColumnsController(): ColumnsControllerPanel = object : ColumnsControllerPanel(table!!) {
    init {
      layout = BorderLayout()
      add(statistics, BorderLayout.CENTER)
      myPreferredSize = statistics.preferredSize
    }

    override fun getPreferredSize(): Dimension = statistics.preferredSize

    override fun computeMyPreferredSize(): Dimension = statistics.preferredSize

    override fun updateColumns() = Unit

    override fun createColumn(columnView: Int): AdditionalPanel = object : AdditionalPanel(table.columnModel.getColumn(columnView)) {
      override fun detach() = Unit
      override fun updateHeight() = Unit
    }
  }
}
