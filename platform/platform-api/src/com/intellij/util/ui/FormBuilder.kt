// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.util.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.util.ui.UIUtil.DEFAULT_VGAP
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.BOTH
import java.awt.GridBagConstraints.CENTER
import java.awt.GridBagConstraints.EAST
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagConstraints.NONE
import java.awt.GridBagConstraints.NORTHEAST
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagConstraints.WEST
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSpinner
import javax.swing.JTextField

@Deprecated("Uses outdated component spacing and alignment. The functionality is covered by Kotlin UI DSL, which should be used instead.")
open class FormBuilder {

  private var alignLabelOnRight = false

  open val panel: JPanel = JPanel(GridBagLayout()).apply {
    UIUtil.applyDeprecatedBackground(this)
  }

  private var lines = 0
  private var vertical = false
  private var verticalGap = DEFAULT_VGAP
  private var horizontalGap = DEFAULT_HGAP
  private var formLeftIndent = 0

  companion object {
    @JvmStatic
    fun createFormBuilder(): FormBuilder = FormBuilder()
  }

  open fun addLabeledComponent(label: JComponent?, component: JComponent): FormBuilder {
    return addLabeledComponent(label, component, verticalGap, false)
  }

  open fun addLabeledComponent(label: JComponent?, component: JComponent, topInset: Int): FormBuilder {
    return addLabeledComponent(label, component, topInset, false)
  }

  open fun addLabeledComponent(label: JComponent?, component: JComponent, labelOnTop: Boolean): FormBuilder {
    return addLabeledComponent(label, component, verticalGap, labelOnTop)
  }

  open fun addLabeledComponent(@NlsContexts.Label labelText: String, component: JComponent): FormBuilder {
    return addLabeledComponent(labelText, component, verticalGap, false)
  }

  open fun addLabeledComponent(@NlsContexts.Label labelText: String, component: JComponent, topInset: Int): FormBuilder {
    return addLabeledComponent(labelText, component, topInset, false)
  }

  open fun addLabeledComponent(@NlsContexts.Label labelText: String, component: JComponent, labelOnTop: Boolean): FormBuilder {
    return addLabeledComponent(labelText, component, verticalGap, labelOnTop)
  }

  open fun addLabeledComponent(
    @NlsContexts.Label labelText: String,
    component: JComponent,
    topInset: Int,
    labelOnTop: Boolean,
  ): FormBuilder {
    val label = createLabelForComponent(labelText, component)
    return addLabeledComponent(label, component, topInset, labelOnTop)
  }

  private fun createLabelForComponent(@NlsContexts.Label labelText: String, component: JComponent): JLabel {
    val label = JLabel(UIUtil.replaceMnemonicAmpersand(labelText))
    label.labelFor = component
    return label
  }

  open fun addComponent(component: JComponent): FormBuilder {
    return addLabeledComponent(null, component, verticalGap, false)
  }

  open fun addComponent(component: JComponent, topInset: Int): FormBuilder {
    return addLabeledComponent(null, component, topInset, false)
  }

  open fun addComponentFillVertically(component: JComponent, topInset: Int): FormBuilder {
    return addLabeledComponent(null, component, topInset, false, true)
  }

  open fun addSeparator(topInset: Int): FormBuilder {
    return addComponent(JSeparator(), topInset)
  }

  open fun addSeparator(): FormBuilder {
    return addSeparator(verticalGap)
  }

  open fun addVerticalGap(height: Int): FormBuilder {
    if (height == -1) {
      panel.add(JLabel(), GridBagConstraints(0, lines++, 2, 1, 0.0, 1.0, CENTER, NONE, JBInsets.emptyInsets(), 0, 0))
      return this
    }

    return addLabeledComponent(null,
                               Box.Filler(JBDimension(0, height), JBDimension(0, height), JBDimension(Short.MAX_VALUE.toInt(), height)))
  }

  open fun addTooltip(@NlsContexts.Label text: String): FormBuilder {
    val label = JBLabel(text, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER)
    label.border = JBUI.Borders.emptyLeft(10)
    return addComponentToRightColumn(label, 1)
  }

  open fun addComponentToRightColumn(component: JComponent): FormBuilder {
    return addComponentToRightColumn(component, verticalGap)
  }

  open fun addComponentToRightColumn(component: JComponent, topInset: Int): FormBuilder {
    return addLabeledComponent(JLabel(), component, topInset)
  }

  open fun addLabeledComponent(
    label: JComponent?,
    component: JComponent,
    topInset: Int,
    labelOnTop: Boolean,
  ): FormBuilder {
    val fillVertically = component is JScrollPane
    return addLabeledComponent(label, component, topInset, labelOnTop, fillVertically)
  }

  open fun addLabeledComponentFillVertically(@NlsContexts.Label labelText: String, component: JComponent): FormBuilder {
    val label = createLabelForComponent(labelText, component)
    return addLabeledComponent(label, component, verticalGap, true, true)
  }

  private fun addLabeledComponent(
    label: JComponent?,
    component: JComponent,
    topInset: Int,
    labelOnTop: Boolean,
    fillVertically: Boolean,
  ): FormBuilder {
    val c = GridBagConstraints()
    val actualTopInset = if (lines > 0) topInset else 0

    if (vertical || labelOnTop || label == null) {
      c.gridwidth = 2
      c.gridx = 0
      c.gridy = lines
      c.weightx = 0.0
      c.weighty = 0.0
      c.fill = NONE
      c.anchor = getLabelAnchor(false, fillVertically)
      c.insets = JBUI.insets(actualTopInset, formLeftIndent, DEFAULT_VGAP, 0)

      if (label != null) panel.add(label, c)

      c.gridx = 0
      c.gridy = lines + 1
      c.weightx = 1.0
      c.weighty = getWeightY(fillVertically)
      c.fill = getFill(component, fillVertically)
      c.anchor = WEST
      c.insets = JBUI.insets(if (label == null) actualTopInset else 0, formLeftIndent, 0, 0)

      panel.add(component, c)

      lines += 2
    }
    else {
      c.gridwidth = 1
      c.gridx = 0
      c.gridy = lines
      c.weightx = 0.0
      c.weighty = 0.0
      c.fill = NONE
      c.anchor = getLabelAnchor(true, fillVertically)
      c.insets = JBUI.insets(actualTopInset, formLeftIndent, 0, horizontalGap)

      panel.add(label, c)

      c.gridx = 1
      c.weightx = 1.0
      c.weighty = getWeightY(fillVertically)
      c.fill = getFill(component, fillVertically)
      c.anchor = WEST
      c.insets = JBUI.insetsTop(actualTopInset)

      panel.add(component, c)

      lines++
    }

    return this
  }

  private fun getLabelAnchor(honorAlignment: Boolean, fillVertically: Boolean): Int {
    if (fillVertically) return if (honorAlignment && alignLabelOnRight) NORTHEAST else NORTHWEST
    return if (honorAlignment && alignLabelOnRight) EAST else WEST
  }

  protected open fun getFill(component: JComponent?): Int {
    if (component is JComboBox<*> ||
        component is JSpinner ||
        component is JButton ||
        component is JTextField && component.columns != 0) {
      return NONE
    }
    return HORIZONTAL
  }

  private fun getFill(component: JComponent, fillVertically: Boolean): Int {
    if (fillVertically) {
      return BOTH
    }
    return getFill(component)
  }

  private fun getWeightY(fillVertically: Boolean): Double {
    return if (fillVertically) 1.0 else 0.0
  }

  @get:ApiStatus.Internal
  open val lineCount: Int
    get() = lines

  open fun setAlignLabelOnRight(alignLabelOnRight: Boolean): FormBuilder {
    this.alignLabelOnRight = alignLabelOnRight
    return this
  }

  open fun setVertical(vertical: Boolean): FormBuilder {
    this.vertical = vertical
    return this
  }

  open fun setVerticalGap(verticalGap: Int): FormBuilder {
    this.verticalGap = verticalGap
    return this
  }

  open fun setHorizontalGap(horizontalGap: Int): FormBuilder {
    this.horizontalGap = horizontalGap
    return this
  }

  open fun setFormLeftIndent(formLeftIndent: Int): FormBuilder {
    this.formLeftIndent = formLeftIndent
    return this
  }
}
