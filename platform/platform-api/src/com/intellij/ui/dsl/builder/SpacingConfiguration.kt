// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI

/**
 * List of all configurable spacings for building Kotlin UI DSL panels. All returned values are unscaled
 */
interface SpacingConfiguration {

  /**
   * Small horizontal gap, used between label and related component for example
   */
  val horizontalSmallGap: Int

  /**
   * Default horizontal gap between components in one row
   */
  val horizontalDefaultGap: Int

  /**
   * Horizontal gap between two columns of components
   */
  val horizontalColumnsGap: Int

  /**
   * Horizontal gap between component and related comment
   */
  val horizontalCommentGap: Int

  /**
   * The horizontal left indent of one level
   */
  val horizontalIndent: Int

  /**
   * The horizontal left indent for toggle button comment
   */
  val horizontalToggleButtonIndent: Int

  /**
   * Top and bottom gaps for components like CheckBox, JTextField etc
   */
  val verticalComponentGap: Int

  /**
   * Vertical small gap between unrelated settings
   */
  val verticalSmallGap: Int

  /**
   * Vertical medium gap, for example used before and after groups
   */
  val verticalMediumGap: Int

  /**
   * Vertical gap after button group header
   */
  val buttonGroupHeaderBottomGap: Int

  /**
   * Vertical gaps between text and button border for segmented buttons
   */
  val segmentedButtonVerticalGap: Int

  /**
   * Horizontal gaps between text and button border for segmented buttons
   */
  val segmentedButtonHorizontalGap: Int

  /**
   * Unscaled gaps between dialog content and its content
   */
  val dialogUnscaledGaps: UnscaledGaps get() = UnscaledGaps.EMPTY
}

open class EmptySpacingConfiguration : SpacingConfiguration {
  override val horizontalSmallGap: Int = 0
  override val horizontalDefaultGap: Int = 0
  override val horizontalColumnsGap: Int = 0
  override val horizontalCommentGap: Int = 0
  override val horizontalIndent: Int = 0
  override val horizontalToggleButtonIndent: Int = 0
  override val verticalComponentGap: Int = 0
  override val verticalSmallGap: Int = 0
  override val verticalMediumGap: Int = 0
  override val buttonGroupHeaderBottomGap: Int = 0
  override val segmentedButtonVerticalGap: Int = 0
  override val segmentedButtonHorizontalGap: Int = 0
  override val dialogUnscaledGaps: UnscaledGaps = UnscaledGaps.EMPTY
}

open class IntelliJSpacingConfiguration : SpacingConfiguration {
  override val horizontalSmallGap: Int = 6
  override val horizontalDefaultGap: Int = 16
  override val horizontalColumnsGap: Int = 60
  override val horizontalCommentGap: Int = 12
  override val horizontalIndent: Int = calculateHorizontalIndent()
  override val horizontalToggleButtonIndent: Int = calculateHorizontalIndent()

  override val verticalComponentGap: Int = 6
  override val verticalSmallGap: Int = 8
  override val verticalMediumGap: Int = 20
  override val buttonGroupHeaderBottomGap: Int = 2
  override val segmentedButtonVerticalGap: Int = 3
  override val segmentedButtonHorizontalGap: Int = 12
  override val dialogUnscaledGaps: UnscaledGaps = UnscaledGaps(10, 12, 10, 12)
}

/**
 * Returns space between left visible part of CheckBox and text
 *
 * See [com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI] and [com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxBorder]
 */
private fun calculateHorizontalIndent(): Int {
  val iconSize = JBUIScale.scale(JBUI.getInt("CheckBox.iconSize", 18))
  val textIconGap = JBUIScale.scale(JBUI.getInt("CheckBox.textIconGap", 5))
  val insets = JBUI.insets("CheckBox.borderInsets", JBUI.insets(1))
  return iconSize - insets.left + textIconGap
}
