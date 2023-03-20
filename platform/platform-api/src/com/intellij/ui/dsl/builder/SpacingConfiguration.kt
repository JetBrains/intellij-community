// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.gridLayout.UnscaledGaps

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
   * Vertical medium gap between unrelated settings, before and after groups
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
   * Gaps between dialog content and its content
   */
  val dialogGap: UnscaledGaps
}

open class EmptySpacingConfiguration : SpacingConfiguration {
  override val horizontalSmallGap = 0
  override val horizontalDefaultGap = 0
  override val horizontalColumnsGap = 0
  override val horizontalIndent = 0
  override val horizontalToggleButtonIndent = 0
  override val verticalComponentGap = 0
  override val verticalSmallGap = 0
  override val verticalMediumGap = 0
  override val buttonGroupHeaderBottomGap = 0
  override val segmentedButtonVerticalGap = 0
  override val segmentedButtonHorizontalGap = 0
  override val dialogGap = UnscaledGaps.EMPTY
}

open class IntelliJSpacingConfiguration : SpacingConfiguration {
  override val horizontalSmallGap = 6
  override val horizontalDefaultGap = 16
  override val horizontalColumnsGap = 60
  override val horizontalIndent = 20
  override val horizontalToggleButtonIndent = 20
  override val verticalComponentGap = 6
  override val verticalSmallGap = 8
  override val verticalMediumGap = 20
  override val buttonGroupHeaderBottomGap = 2
  override val segmentedButtonVerticalGap = 3
  override val segmentedButtonHorizontalGap = 12
  override val dialogGap = UnscaledGaps(10, 12, 10, 12)
}
