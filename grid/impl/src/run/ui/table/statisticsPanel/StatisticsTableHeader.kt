package com.intellij.database.run.ui.table.statisticsPanel

import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.AdditionalTableHeader
import com.intellij.ui.table.ColumnHeaderController
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel
import kotlin.math.max

abstract class StatisticsTableHeader(statisticsPanelMode: StatisticsPanelMode = StatisticsPanelMode.OFF) : JPanel(BorderLayout()) {
  var statisticsPanelMode: StatisticsPanelMode = statisticsPanelMode
    set(value) {
      field = value
      (columnsController as? StatisticsColumnsControllerPanel)?.setMode(value)

      view?.revalidate()
      view?.repaint()
    }

  var view: TableResultView? = null
    private set

  var position: AdditionalTableHeader.Position = DEFAULT_POSITION
    set(value) {
      field = value
      view?.setStatisticsHeaderPosition(value)
    }

  protected var columnsController: ColumnsControllerPanel? = null

  @JvmName("installView")
  internal fun installView(view: TableResultView?) {
    columnsController?.detach()
    removeAll()
    this.view = view
    columnsController = if (view == null) null else createColumnsController().also { add(it, BorderLayout.WEST) }
    (columnsController as? StatisticsColumnsControllerPanel)?.setMode(statisticsPanelMode)
    revalidate()
  }

  fun updateStatisticsTableHeader() {
    statisticsPanelMode = statisticsPanelMode
  }

  abstract fun createColumnsController(): ColumnsControllerPanel

  abstract inner class StatisticsColumnsControllerPanel(table: TableResultView) : ColumnsControllerPanel(table) {
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
      if (--autoRun == 0) {
        updateHeight()
      }
    }

    override fun getPreferredSize(): Dimension {
      val table: TableResultView = table
      myPreferredSize.width = table.width
      return myPreferredSize
    }

    abstract inner class StatisticsPanel(tc: TableColumn) : AdditionalPanel(tc) {
      var panel: Component? = null
      protected var offStatisticsPanel: DialogPanel = panel { }
      protected var compactStatisticsPanel: @Nls String? = null
      protected var detailedStatisticsPanel: @Nls String? = null

      override fun detach() {
        tableColumn.removePropertyChangeListener(this)
      }

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

  companion object {
    val DEFAULT_POSITION: AdditionalTableHeader.Position = AdditionalTableHeader.Position.INLINE
  }

  abstract class ColumnsControllerPanel(val table: TableResultView) : ColumnHeaderController<ColumnsControllerPanel.AdditionalPanel>(
    object : Context {
      override val columnModel: TableColumnModel get() = table.columnModel
      override val model: TableModel get() = table.model
      override val componentOrientation: ComponentOrientation get() = table.componentOrientation
      override val draggedColumn: TableColumn? get() = table.draggedColumn
      override val draggedDistance: Int get() = table.headerDraggedDistance
    }
  ) {
    abstract override fun createColumn(columnView: Int): AdditionalPanel

    abstract inner class AdditionalPanel(tableColumn: TableColumn) : ColumnPanel(tableColumn, ::placeComponents)
  }
}
