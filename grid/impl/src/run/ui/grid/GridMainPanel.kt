package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridPanel
import com.intellij.database.datagrid.GridPanel.ViewPosition
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.RemovableView
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class GridMainPanel(val grid: DataGrid,
                    private val dataProvider: UiDataProvider
) : JBLoadingPanel(BorderLayout(), grid), GridPanel, UiDataProvider {
  private val myHorizontalSplitter: OnePixelSplitter

  /**
   * needed to paint border over all components except header
   * Setting border to OnePixelSplitter has no effect
   */
  private val myVerticalSplitterWrapper = JPanel(BorderLayout())
  private val myVerticalSplitter: OnePixelSplitter
  private val myPanelWithFirstHeader = JPanel(BorderLayout())
  private val myCentralPanel = JPanel(BorderLayout())

  private var bottomView: RemovableView? = null
  private var rightView: RemovableView? = null

  init {
    add(myPanelWithFirstHeader, BorderLayout.CENTER)
    myPanelWithFirstHeader.isOpaque = false
    myVerticalSplitter = OnePixelSplitter(true, 0.7f)
    myHorizontalSplitter = OnePixelSplitter(false, 0.7f)
    myVerticalSplitterWrapper.add(myVerticalSplitter, BorderLayout.CENTER)
    myPanelWithFirstHeader.add(myVerticalSplitterWrapper, BorderLayout.CENTER)
    myVerticalSplitter.firstComponent = myHorizontalSplitter
    myHorizontalSplitter.firstComponent = myCentralPanel
  }

  private val sideView = object {
    operator fun get(position: ViewPosition): RemovableView? {
      return when (position) {
        ViewPosition.RIGHT -> rightView
        ViewPosition.BOTTOM -> bottomView
      }
    }
    operator fun set(position: ViewPosition, view: RemovableView?) {
      when (position) {
        ViewPosition.RIGHT -> {
          rightView = view
          myHorizontalSplitter.secondComponent = view?.viewComponent
        }
        ViewPosition.BOTTOM -> {
          bottomView = view
          myVerticalSplitter.secondComponent = view?.viewComponent
        }
      }
    }
    fun locate(view: RemovableView): ViewPosition? {
      if (rightView == view) {
        return ViewPosition.RIGHT
      }
      if (bottomView == view) {
        return ViewPosition.BOTTOM
      }
      return null
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink.uiDataSnapshot(dataProvider)
  }

  override fun getPreferredSize(): Dimension {
    val resultViewSize = grid.mainResultViewComponent.preferredSize
    val toolbar = topComponent

    val verticalHeader = getRightHeaderComponent()
    val verticalHeaderWidth = verticalHeader?.preferredSize?.width ?: 0
    val bordersWidth = insets.left + insets.right
    val width = (max((toolbar?.preferredSize?.width ?: 0).toDouble(),
                     resultViewSize.width.toDouble()) + bordersWidth + verticalHeaderWidth).toInt()

    val toolbarHeight = toolbar?.preferredSize?.height ?: 0
    val bordersHeight = insets.top + insets.bottom
    val bottomHeader = bottomHeaderComponent
    val bottomToolbarHeight = bottomHeader?.preferredSize?.height ?: 0

    val secondBottomComponent = secondBottomComponent
    val secondBottomComponentHeight = secondBottomComponent?.preferredSize?.height ?: 0
    val height = resultViewSize.height + toolbarHeight + bordersHeight + bottomToolbarHeight + secondBottomComponentHeight

    return Dimension(width, height)
  }

  override fun getBackground(): Color {
    @Suppress("SENSELESS_COMPARISON")
    if (grid == null) { // for calls in super before grid initialization
      return UIUtil.getTableBackground();
    }
    return grid.colorsScheme.defaultBackground;
  }

  override fun getTopComponent(): Component? {
    return getComponentAt(myPanelWithFirstHeader, BorderLayout.NORTH)
  }

  override fun getSecondTopComponent(): Component? {
    return getComponentAt(myCentralPanel, BorderLayout.NORTH)
  }

  var secondBottomComponent: JComponent?
    get() = (getComponentAt(contentPanel, BorderLayout.SOUTH) as? JComponent)
    set(component) {
      replaceComponentAt(contentPanel, BorderLayout.SOUTH, component)
    }

  fun setCenterComponent(component: JComponent) {
    replaceComponentAt(myCentralPanel, BorderLayout.CENTER, component)
  }

  override fun getCenterComponent(): JComponent {
    return myVerticalSplitterWrapper
  }

  override fun setTopComponent(topComponent: Component?) {
    replaceComponentAt(myPanelWithFirstHeader, BorderLayout.NORTH, topComponent)
  }

  override fun setRightHeaderComponent(topComponent: Component?) {
    replaceComponentAt(myPanelWithFirstHeader, BorderLayout.EAST, topComponent)
  }

  private fun getRightHeaderComponent(): JComponent? {
    return (getComponentAt(myPanelWithFirstHeader, BorderLayout.EAST) as? JComponent)
  }

  override fun setBottomHeaderComponent(topComponent: JComponent?) {
    replaceComponentAt(myPanelWithFirstHeader, BorderLayout.SOUTH, topComponent)
  }

  override fun getBottomHeaderComponent(): JComponent? {
    return (getComponentAt(myPanelWithFirstHeader, BorderLayout.SOUTH) as? JComponent)
  }

  override fun setSecondTopComponent(topComponent: Component?) {
    replaceComponentAt(myCentralPanel, BorderLayout.NORTH, topComponent)
  }

  fun setBottomComponent(bottomComponent: Component?) {
    replaceComponentAt(myCentralPanel, BorderLayout.SOUTH, bottomComponent)
  }

  override fun getComponent(): JBLoadingPanel {
    return this
  }

  override fun getSideView(viewPosition: ViewPosition): RemovableView? {
    return sideView[viewPosition]
  }

  override fun putSideView(view: RemovableView, newPosition: ViewPosition, oldPosition: ViewPosition?) {
    if (oldPosition != null) {
      sideView[oldPosition] = null
    }
    sideView[newPosition]?.onRemoved()
    sideView[newPosition] = view
  }

  override fun removeSideView(view: RemovableView) {
    sideView.locate(view)?.let { location ->
      sideView[location]?.onRemoved();
      sideView[location] = null
    }
  }

  override fun locateSideView(view: RemovableView): ViewPosition? {
    return sideView.locate(view)
  }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    GridUtil.globalSchemeChange(grid, scheme)
  }

  companion object {
    private fun replaceComponentAt(panel: JPanel, location: String, with: Component?) {
      val toReplace = getComponentAt(panel, location)
      if (toReplace != null) {
        panel.remove(toReplace)
      }
      if (with != null) {
        panel.add(with, location)
      }
      panel.revalidate()
      panel.repaint()
    }

    private fun getComponentAt(panel: JPanel, location: String): Component? {
      return (panel.layout as BorderLayout).getLayoutComponent(location)
    }
  }
}
