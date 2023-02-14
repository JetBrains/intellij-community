package com.intellij.codeInsight.codeVision.ui.popup.layouter

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

val Rectangle.left: Int get() = x
val Rectangle.right: Int get() = x + width
val Rectangle.top: Int get() = y
val Rectangle.bottom: Int get() = y + height

fun Rectangle.inflate(size: Dimension): Rectangle = inflate(size.width, size.height)
fun Rectangle.inflate(w: Int, h: Int): Rectangle = Rectangle(this).apply {
  x -= w
  y -= h
  width += 2 * w
  height += 2 * h
}

fun Rectangle.setRight(right: Int) {
  width += right - this.right
}

fun Rectangle.setLeft(left: Int) {
  width -= left - this.left
  x = left
}

fun Rectangle.setBottom(bottom: Int) {
  height += bottom - this.bottom
}

fun Rectangle.setTop(top: Int) {
  height -= top - this.top
  y = top
}


fun Rectangle.getCenter(): Point = Point((left + right) / 2, (top + bottom) / 2)

fun rectangleFromLTRB(left: Int, top: Int, right: Int, bottom: Int): Rectangle = Rectangle(left, top, right - left, bottom - top)

/**
 * Creates a rectangle without negative dimensions.
 * If near is less than far, they're both set to the middle value.
 */
fun rectangleFromLTRBNonNegative(left: Int, top: Int, right: Int, bottom: Int): Rectangle {
  var l = left
  var r = right
  var t = top
  var b = bottom
  if (l > r) {
    l = (l + r) / 2
    r = l
  }
  if (t > b) {
    t = (t + b) / 2
    b = t
  }
  return rectangleFromLTRB(l, t, r, b)
}

/**
 * Performs the smart clipping that returns a non-all-zeros rectangle even if there is no intersection.
 * Ie, when the source rect lays outside the bounds over some side, the result is a zero-thick projection on that side.
 */
fun Rectangle.smartClip(bounds: Rectangle): Rectangle {
  assert(this.width >= 0 && this.height >= 0)
  assert(bounds.width >= 0 && bounds.height >= 0)

  val r = smartClip(right, bounds.left, bounds.right)
  val l = smartClip(left, bounds.left, bounds.right)
  val t = smartClip(top, bounds.top, bounds.bottom)
  val b = smartClip(bottom, bounds.top, bounds.bottom)

  return rectangleFromLTRBNonNegative(l, t, r, b)
}

fun Rectangle.horizontalSmartClip(bounds: Rectangle): Rectangle {
  assert(this.width >= 0)
  assert(bounds.width >= 0)

  val r = smartClip(right, bounds.left, bounds.right)
  val l = smartClip(left, bounds.left, bounds.right)

  return rectangleFromLTRBNonNegative(l, top, r, bottom)
}

fun Rectangle.verticalSmartClip(bounds: Rectangle): Rectangle {
  assert(this.width >= 0)
  assert(bounds.width >= 0)

  val t = smartClip(top, bounds.top, bounds.bottom)
  val b = smartClip(bottom, bounds.top, bounds.bottom)

  return rectangleFromLTRBNonNegative(left, t, right, b)
}

private fun smartClip(a: Int, a1: Int, a2: Int): Int {
  return if (a > a1) if (a < a2) a else a2 else a1
}

fun Rectangle.map(delegate: (Rectangle) -> Rectangle?): Rectangle? {
  return delegate(this@map)
}