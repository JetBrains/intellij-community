// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Toolkit
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

/**
 * Strips any horizontal insets ([EmptyBorder], [JBEmptyBorder]) and ipads ([SimpleColoredComponent.getIpad])
 */
@ApiStatus.Internal
fun stripHorizontalInsets(component: JComponent) {
  component.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)

  val border = component.border
  if (border != null && (border.javaClass === EmptyBorder::class.java || border.javaClass === JBEmptyBorder::class.java)) {
    val insets = (border as EmptyBorder).borderInsets
    @Suppress("UseDPIAwareBorders")
    component.border = EmptyBorder(insets.top, 0, insets.bottom, 0)
  }

  if (component is SimpleColoredComponent) {
    @Suppress("UseDPIAwareInsets")
    component.ipad = Insets(component.ipad.top, 0, component.ipad.bottom, 0)
  }
}

/**
 * Must return the same result as union of methods [SimpleColoredComponent.updateUI] and [SimpleColoredComponent.applyAdditionalHints]
 */
@ApiStatus.Internal
fun getDefaultTextRenderingHints(): MutableMap<RenderingHints.Key, Any?> {
  if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
    val defaultFractionalMetrics = UIManager.getDefaults()[RenderingHints.KEY_FRACTIONALMETRICS]
                                   ?: RenderingHints.VALUE_FRACTIONALMETRICS_OFF

    return mutableMapOf<RenderingHints.Key, Any?>(
      RenderingHints.KEY_TEXT_LCD_CONTRAST to UIUtil.getLcdContrastValue(),
      RenderingHints.KEY_FRACTIONALMETRICS to defaultFractionalMetrics,
      RenderingHints.KEY_TEXT_ANTIALIASING to AntialiasingType.getKeyForCurrentScope(false)
    )
  }
  else {
    // Cannot use services while Application has not been loaded yet, so let's apply the default hints
    val props = Toolkit.getDefaultToolkit().getDesktopProperty(GraphicsUtil.DESKTOP_HINTS) ?: return mutableMapOf()

    val result = mutableMapOf<RenderingHints.Key, Any?>()
    result.putAll(props as Map<RenderingHints.Key, Any?>)
    return result
  }
}
