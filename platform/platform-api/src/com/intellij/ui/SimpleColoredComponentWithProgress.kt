package com.intellij.ui

import com.intellij.util.ui.AsyncProcessIcon
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent

@ApiStatus.Internal
class SimpleColoredComponentWithProgress : SimpleColoredComponent() {
  private var progressIcon: AsyncProcessIcon = RightCentered("loading").also {
    isVisible = true
  }

  init {
    add(progressIcon)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    updateIconLocation()
  }

  private fun updateIconLocation() {
    if (progressIcon.isVisible) {
      progressIcon.updateLocation(this)
    }
  }

  fun startLoading() {
    progressIcon.isVisible = true
    progressIcon.resume()
    fullRepaint()
  }

  fun stopLoading() {
    progressIcon.suspend()
    progressIcon.isVisible = false
    fullRepaint()
  }

  private fun fullRepaint() {
    doLayout()
    revalidate()
    repaint()
  }

  override fun clear() {
    super.clear()
    add(progressIcon)
  }

  class RightCentered(name: String) : AsyncProcessIcon(name) {
    override fun calculateBounds(container: JComponent): Rectangle {
      val size = container.size
      val iconSize = preferredSize
      return Rectangle(size.width - iconSize.width, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height)
    }
  }
}