package com.intellij.database.run.ui.table.statisticsPanel

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.AdditionalTableHeader
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableColumn
import kotlin.math.max

abstract class StatisticsTableHeader(statisticsPanelMode: StatisticsPanelMode = StatisticsPanelMode.OFF): AdditionalTableHeader() {
  var statisticsPanelMode: StatisticsPanelMode = statisticsPanelMode
    set(value) {
      field = value
      (columnsController as StatisticsColumnsControllerPanel).setMode(value)

      table?.revalidate()
      table?.repaint()
    }

  override var table: JTable? = null

  companion object {
    val DEFAULT_POSITION: Position = Position.INLINE
  }

  override fun detachController() {
    columnsController?.detach()
  }

  abstract inner class StatisticsColumnsControllerPanel(table: JTable): ColumnsControllerPanel(table) {
    abstract fun setMode(statisticsPanelMode: StatisticsPanelMode)

    override fun computeMyPreferredSize(): Dimension {
      val count = tableColumnModel.columnCount
      return Dimension(0, if (count == 0) 0 else columns[0].myHeight)
    }

    /** Computes the proper preferred height-width is not important-.  */
    private fun updateHeight() {
      var h = 0
      for (c in columns) {
        h = max(h, c.myHeight)
      }

      myPreferredSize.height = h

      placeComponents()
      repaint()
    }

    override fun updateColumns() {
      // see the comment on columnAdded
      if (--autoRun == 0) {
        updateHeight()
      }
    }

    override fun getPreferredSize(): Dimension {
      val table: JTable = table
      myPreferredSize.width = table.width
      return myPreferredSize
    }

    abstract inner class StatisticsPanel(tc: TableColumn) : AdditionalPanel(tc) {
      var panel: Component? = null
      protected var offStatisticsPanel: DialogPanel = panel {  }
      protected var compactStatisticsPanel: @Nls String? = null
      protected var detailedStatisticsPanel: @Nls String? = null

      override fun detach() {}

      override fun updateHeight() {
        myHeight = getPreferredSize().height
        revalidate()
      }

      abstract fun createCompactStatistics(): String?

      abstract fun createDetailedStatistics(): String?

      open fun setMode(newMode: StatisticsPanelMode) {
        val newPanel: DialogPanel = when (newMode) {
          StatisticsPanelMode.OFF -> offStatisticsPanel

          StatisticsPanelMode.COMPACT -> {
            if (compactStatisticsPanel == null) {
              compactStatisticsPanel = createCompactStatistics()
            }
            compactStatisticsPanel?.let { createPanelWithLabel(it) }
          }

          StatisticsPanelMode.DETAILED -> {
            if (detailedStatisticsPanel == null) {
              detailedStatisticsPanel = createDetailedStatistics()
            }
            detailedStatisticsPanel?.let { createPanelWithLabel(it) }
          }
        } ?: return


        resetPanel(newPanel)
      }

      private fun createPanelWithLabel(labelString: @NlsContexts.Label String): DialogPanel {
        return panel {
          row {
            label(labelString).applyToComponent {
              putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false))
            }
          }
        }
      }

      protected fun resetPanel(newPanel: JPanel) {
        remove(0)

        panel = newPanel
        add(newPanel, BorderLayout.CENTER)
        table.revalidate()

        update()
      }
    }
  }
}