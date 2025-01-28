package com.intellij.database.run.ui

import com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY
import com.intellij.database.datagrid.*
import com.intellij.database.extractors.DisplayType
import com.intellij.database.datagrid.RemovableView
import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.SideBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import javax.swing.JComponent

/**
 * @author Liudmila Kornilova
 **/
class EditMaximizedView(private val grid: DataGrid) : CheckedDisposable, RemovableView {
  @Volatile
  private var disposed = false

  private val runnerTabs = object : JBRunnerTabs(grid.project, grid) {
    override fun uiDataSnapshot(sink: DataSink) {
      super.uiDataSnapshot(sink)
      sink[EDIT_MAXIMIZED_KEY] = this@EditMaximizedView
    }
  }
  override val viewComponent: JComponent = runnerTabs
  private val tabInfoProviders = ValueEditorTab.EP.extensionList.filter { it.applicable(grid) }
    .sortedByDescending { it.priority }.map { tab ->
      tab.createTabInfoProvider(grid) { open { it is ValueTabInfoProvider } }
    }
  val preferedFocusComponent: JComponent?
    get() = runnerTabs.selectedInfo?.getPreferredFocusableComponent()

  init {
    (runnerTabs.component.border as? JBRunnerTabs.JBRunnerTabsBorder)?.setSideMask(SideBorder.NONE)
    for (provider in tabInfoProviders) {
      Disposer.register(this, provider)
      provider.update()
      runnerTabs.addTab(provider.tabInfo)
    }

    runnerTabs.addListener(
      object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          val provider = tabInfoProviders.find { it.tabInfo == newSelection }
          val oldProvider = tabInfoProviders.find { it.tabInfo == oldSelection }
          oldProvider?.onTabLeave()
          provider?.onTabEnter()

          if (provider == null) {
            return
          }
          reportOpenProvider(provider)
        }
      },
      this
    )

    grid.addDataGridListener(object : DataGridListener {
      override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
        ApplicationManager.getApplication().invokeLater {
          if (isDisposed) return@invokeLater
          // MergingUpdateQueue in DataGridUtil.createEDTSafeWrapper will cause "Write-unsafe context" exception
          // without this invokeLater
          tabInfoProviders.forEach { provider ->
            if (place !is EditMaximizedViewRequestPlace || place.viewer != provider.getViewer() || place.grid != this@EditMaximizedView.grid) {
              provider.update(event = UpdateEvent.ContentChanged)
            }
          }
        }
      }

      override fun onCellLanguageChanged(columnIdx: ModelIndex<GridColumn>, language: Language) {
        tabInfoProviders.forEach { it.update() }
      }

      override fun onCellDisplayTypeChanged(columnIdx: ModelIndex<GridColumn>, type: DisplayType) {
        tabInfoProviders.forEach { it.update() }
      }

      override fun onSelectionChanged(dataGrid: DataGrid?) {
        tabInfoProviders.forEach { it.update(event = UpdateEvent.SelectionChanged) }
      }

      override fun onValueEdited(dataGrid: DataGrid?, value: Any?) {
        ApplicationManager.getApplication().invokeLater {
          tabInfoProviders.forEach { it.update(event = UpdateEvent.ValueChanged(value)) }
        }
      }
    }, this)

    grid.putUserData(EDIT_MAXIMIZED_GRID_KEY, this)
  }

  fun getAggregateViewer(): CellViewer? {
    val tabInfoProvider = tabInfoProviders.find { tabInfoProvider -> tabInfoProvider is AggregatesTabInfoProvider }
    return tabInfoProvider?.getViewer()
  }

  fun getRecordViewer(): RecordView? {
    return tabInfoProviders.find { it is RecordViewInfoProvider }?.getViewer() as? RecordView
  }

  fun open(select: (TabInfoProvider) -> Boolean) {
    val tabInfoProvider = tabInfoProviders.find { tabInfoProvider -> select(tabInfoProvider) }
    if (tabInfoProvider != null) {
      val current = getCurrentTabInfoProvider()
      current.onTabLeave()
      tabInfoProvider.onTabEnter()
      if (tabInfoProvider == current) {
        reportOpenProvider(tabInfoProvider)
      }
      runnerTabs.select(tabInfoProvider.tabInfo, true)
    }
  }

  private fun reportOpenProvider(provider: TabInfoProvider) {
    val listener = GridUtil.activeGridListener()
    when (provider) {
      is ValueTabInfoProvider -> listener.onValueEditorOpened(grid)
      is AggregatesTabInfoProvider -> listener.onAggregateViewOpened(grid)
      is RecordViewInfoProvider -> listener.onRecordViewOpened(grid)
    }
  }

  override fun dispose() {
    disposed = true
    grid.putUserData(EDIT_MAXIMIZED_GRID_KEY, null)
  }

  override fun isDisposed(): Boolean {
    return disposed
  }

  override fun onRemoved() {
    Disposer.dispose(this)
  }

  fun getCurrentTabInfoProvider(): TabInfoProvider {
    return tabInfoProviders.find { runnerTabs.selectedInfo == it.tabInfo }!!
  }
}

@JvmField
val EDIT_MAXIMIZED_KEY: DataKey<EditMaximizedView> = DataKey.create("EDIT_MAXIMIZED_KEY")
@JvmField
val EDIT_MAXIMIZED_GRID_KEY: Key<EditMaximizedView> = Key("EDIT_MAXIMIZED_GRID_KEY")

fun findEditMaximized(context: DataContext): EditMaximizedView? {
  val view = context.getData(EDIT_MAXIMIZED_KEY)
  if (view != null) return view
  val grid = context.getData(DATA_GRID_KEY) ?: return null
  return grid.getUserData(EDIT_MAXIMIZED_GRID_KEY)
}
