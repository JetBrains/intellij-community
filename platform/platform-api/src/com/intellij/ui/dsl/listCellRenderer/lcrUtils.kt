// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBEmptyBorder
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets
import javax.swing.JComponent
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
