package com.intellij.grid.images.impl

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.extractors.ImageInfo
import com.intellij.database.run.ui.CellViewer
import com.intellij.database.run.ui.CellViewerFactory
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.Suitability
import com.intellij.database.run.ui.UpdateEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import org.intellij.images.editor.impl.ImageEditorManagerImpl
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author Liudmila Kornilova
 **/
class ImageCellViewer(private val grid: DataGrid) : CellViewer, CheckedDisposable by Disposer.newCheckedDisposable() {
  private val panel = JPanel(BorderLayout())
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  override val component: JComponent = panel
  override val preferedFocusComponent: JComponent? = null

  override fun update(event: UpdateEvent?) {
    if (event is UpdateEvent.ValueChanged) {
      when(val eventValue = event.value) {
        is ImageInfo -> update(eventValue)
        else -> clearContent()
      }
      return
    }

    val rowIdx = grid.selectionModel.leadSelectionRow
    val columnIdx = grid.selectionModel.leadSelectionColumn
    if (!rowIdx.isValid(grid) || !columnIdx.isValid(grid)) {
      clearContent()
      return
    }
    val info = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(rowIdx, columnIdx) as? ImageInfo
    if (info == null) clearContent()
    else update(info)
  }

  private fun clearContent() {
    val prev = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
    if (prev != null) {
      panel.remove(prev)
      if (prev is Disposable) {
        Disposer.dispose(prev)
      }
    }
  }

  private fun showImage(image: BufferedImage) {
    clearContent()
    val ui: JComponent = ImageEditorManagerImpl.createImageEditorUI(image)
    if (ui is Disposable) {
      Disposer.register(this, ui)
    }
    panel.add(ui, BorderLayout.CENTER)
    // revalidate-repaint forces image to appear immediately
    panel.revalidate()
    panel.repaint()
  }

  private fun update(info: ImageInfo) {
    alarm.cancelAllRequests()
    alarm.addRequest(
      {
        val image = info.createImage() ?: return@addRequest
        ApplicationManager.getApplication().invokeLater {
          if (!isDisposed) {
            showImage(image)
          }
        }
      }, 0)
  }

  override fun dispose() { }
}

class ImageCellViewerFactory : CellViewerFactory {
  override fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability {
    if (!row.isValid(grid) || !column.isValid(grid)) return Suitability.NONE
    val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
    return if (value is ImageInfo) Suitability.MAX else Suitability.NONE
  }

  override fun createViewer(grid: DataGrid): CellViewer = ImageCellViewer(grid)
}
