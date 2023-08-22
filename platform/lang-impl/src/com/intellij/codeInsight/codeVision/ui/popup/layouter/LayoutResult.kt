package com.intellij.codeInsight.codeVision.ui.popup.layouter

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

data class LayoutResult(val room: Rectangle, // The part of screen is available for laying out at the specified disposition.
                        val bounds: Rectangle, // The bounds of the laid out entity.
                        val anchor: Rectangle, // The anchoring rectangle against which the entity has been laid out.
                        val disposition: Anchoring2D)

val LayoutResult.location: Point get() = bounds.location
val LayoutResult.size: Dimension get() = bounds.size
