/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.options.impl

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.ui.JBColor
import com.intellij.util.addOptionTag
import org.intellij.images.options.DefaultImageEditorSettings
import org.intellij.images.options.GridOptions
import org.jdom.Element
import java.awt.Color
import java.beans.PropertyChangeSupport

/**
 * Grid options implementation.
 */
internal class GridOptionsImpl(private val propertyChangeSupport: PropertyChangeSupport) : GridOptions {
  private var showDefault: Boolean = DefaultImageEditorSettings.showGrid
  private var lineMinZoomFactor = DefaultImageEditorSettings.showGridWhenZoomEqualOrMoreThan
  private var lineSpan = DefaultImageEditorSettings.showGridAfterEveryXPixels
  private var lineColor: Color? = null

  override fun isShowDefault(): Boolean {
    return showDefault
  }

  override fun getLineZoomFactor(): Int {
    return lineMinZoomFactor
  }

  override fun getLineSpan(): Int {
    return lineSpan
  }

  override fun getLineColor(): Color {
    return lineColor?:
           EditorColorsManager.getInstance().globalScheme.getColor(GRID_LINE_COLOR_KEY) ?: JBColor.DARK_GRAY
  }

  private fun setLineMinZoomFactor(lineMinZoomFactor: Int) {
    val oldValue = this.lineMinZoomFactor
    if (oldValue != lineMinZoomFactor) {
      this.lineMinZoomFactor = lineMinZoomFactor
      propertyChangeSupport.firePropertyChange(GridOptions.ATTR_LINE_ZOOM_FACTOR, oldValue, this.lineMinZoomFactor)
    }
  }

  private fun setLineSpan(lineSpan: Int) {
    val oldValue = this.lineSpan
    if (oldValue != lineSpan) {
      this.lineSpan = lineSpan
      propertyChangeSupport.firePropertyChange(GridOptions.ATTR_LINE_SPAN, oldValue, this.lineSpan)
    }
  }

  override fun inject(options: GridOptions) {
    showDefault = options.isShowDefault
    setLineMinZoomFactor(options.lineZoomFactor)
    setLineSpan(options.lineSpan)
  }

  override fun setOption(name: String, value: Any): Boolean {
    if (GridOptions.ATTR_SHOW_DEFAULT == name) {
      showDefault = value as Boolean
    }
    else if (GridOptions.ATTR_LINE_ZOOM_FACTOR == name) {
      setLineMinZoomFactor(value as Int)
    }
    else if (GridOptions.ATTR_LINE_SPAN == name) {
      setLineSpan(value as Int)
    }
    else {
      return false
    }
    return true
  }
}