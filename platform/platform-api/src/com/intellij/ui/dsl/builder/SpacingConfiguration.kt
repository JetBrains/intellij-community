// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.toUnscaled
import com.intellij.ui.dsl.gridLayout.unscale
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

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
  val dialogGap: Gaps
}

@ApiStatus.Experimental
interface UnscaledSpacingConfiguration: SpacingConfiguration {
  val dialogUnscaledGap: UnscaledGaps

  @Deprecated("Use dialogUnscaledGap instead")
  override val dialogGap: Gaps
}

open class EmptySpacingConfiguration : UnscaledSpacingConfiguration {
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
  override val dialogGap = Gaps.EMPTY
  override val dialogUnscaledGap = UnscaledGaps.EMPTY
}

open class IntelliJSpacingConfiguration : UnscaledSpacingConfiguration {
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
  override val dialogGap = Gaps(10, 12, 10, 12)
  override val dialogUnscaledGap = UnscaledGaps(10, 12, 10, 12)
}

@Internal
fun SpacingConfiguration.toUnscaled(): UnscaledSpacingConfiguration {
  if (this is UnscaledSpacingConfiguration) return this
  val conf = this
  return object: UnscaledSpacingConfiguration {
    override val horizontalSmallGap: Int = conf.horizontalSmallGap.unscale()
    override val horizontalDefaultGap: Int = conf.horizontalDefaultGap.unscale()
    override val horizontalColumnsGap: Int = conf.horizontalColumnsGap.unscale()
    override val horizontalIndent: Int = conf.horizontalIndent.unscale()
    override val horizontalToggleButtonIndent: Int = conf.horizontalToggleButtonIndent.unscale()
    override val verticalComponentGap: Int = conf.verticalComponentGap.unscale()
    override val verticalSmallGap: Int = conf.verticalSmallGap.unscale()
    override val verticalMediumGap: Int = conf.verticalMediumGap.unscale()
    override val buttonGroupHeaderBottomGap: Int = conf.buttonGroupHeaderBottomGap.unscale()
    override val segmentedButtonVerticalGap: Int = conf.segmentedButtonVerticalGap.unscale()
    override val segmentedButtonHorizontalGap: Int = conf.segmentedButtonHorizontalGap.unscale()
    override val dialogGap: Gaps = Gaps.EMPTY
    override val dialogUnscaledGap: UnscaledGaps = conf.dialogGap.toUnscaled()
  }
}
