// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Animations")

package com.intellij.util.animation

import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.util.function.Consumer
import java.util.function.DoubleConsumer
import java.util.function.DoubleFunction
import java.util.function.IntConsumer
import java.util.function.LongConsumer
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Update [animations] delay time in such a way that
 * animations will be run one by one.
 */
fun makeSequent(vararg animations: Animation): Collection<Animation> {
  for (i in 1 until animations.size) {
    val prev = animations[i - 1]
    val curr = animations[i]
    curr.delay += prev.delay + prev.duration
  }
  return animations.toList()
}

/**
 * Empty animation (do nothing).
 *
 * May be used as an anchor frame for any of [Animation.runWhenScheduled], [Animation.runWhenUpdated] or [Animation.runWhenExpired] methods.
 */
fun animation(): Animation = Animation {}

/**
 * Very common animation.
 */
fun animation(consumer: DoubleConsumer) = Animation(consumer)

fun animation(from: Int, to: Int, consumer: IntConsumer): Animation {
  return Animation(DoubleConsumer { value ->
    consumer.accept((from + value * (to - from)).roundToInt())
  })
}

fun <T> animation(context: AnimationContext<T>, function: DoubleFunction<T>): Animation {
  return Animation.withContext(context, function)
}

fun animation(from: Long, to: Long, consumer: LongConsumer): Animation {
  return Animation(DoubleConsumer { value ->
    consumer.accept((from + value * (to - from)).roundToLong())
  })
}

fun animation(from: Float, to: Float, consumer: FloatConsumer): Animation {
  // To prevent precision lost, convert values into double.
  // Example of precision lost is:
  // 0.9f + (1.0f * (0.1f - 0.9f)) == 0.100000024
  return animation(from.toDouble(), to.toDouble(), DoubleConsumer {
    consumer.accept(it.toFloat())
  })
}

fun animation(from: Double, to: Double, consumer: DoubleConsumer): Animation {
  return Animation(DoubleConsumer { value ->
    consumer.accept(from + value * (to - from))
  })
}

fun animation(from: Point, to: Point, consumer: Consumer<Point>): Animation {
  return Animation(DoublePointFunction(from, to), consumer)
}

fun animation(from: Rectangle, to: Rectangle, consumer: Consumer<Rectangle>): Animation {
  return Animation(DoubleRectangleFunction(from, to), consumer)
}

fun <T> animation(values: Array<T>, consumer: Consumer<T>): Animation {
  return Animation(DoubleArrayFunction(values), consumer)
}

fun animation(from: Dimension, to: Dimension, consumer: Consumer<Dimension>): Animation {
  return Animation(DoubleDimensionFunction(from, to), consumer)
}

fun animation(from: Color, to: Color, consumer: Consumer<Color>): Animation {
  return Animation(DoubleColorFunction(from, to), consumer)
}

fun transparent(color: Color, consumer: Consumer<Color>) = animation(color, ColorUtil.withAlpha(color, 0.0), consumer)

fun <T> consumer(function: DoubleFunction<T>, consumer: Consumer<T>): DoubleConsumer {
  return DoubleConsumer { consumer.accept(function.apply(it)) }
}

private fun text(from: String, to: String): DoubleFunction<String> {
  val shorter = if (from.length < to.length) from else to
  val longer = if (from === shorter) to else from
  if (shorter.length == longer.length || !longer.startsWith(shorter)) {
    val fraction = from.length.toDouble() / (from.length + to.length)
    return DoubleFunction { timeline: Double ->
      if (timeline < fraction) {
        from.substring(0, (from.length * ((fraction - timeline) / fraction)).roundToInt())
      }
      else {
        to.substring(0, (to.length * (timeline - fraction) / (1 - fraction)).roundToInt())
      }
    }
  }
  return if (from === shorter) {
    DoubleFunction { timeline: Double ->
      longer.substring(0, (shorter.length + (longer.length - shorter.length) * timeline).roundToInt())
    }
  }
  else {
    DoubleFunction { timeline: Double ->
      longer.substring(0, (longer.length - (longer.length - shorter.length) * timeline).roundToInt())
    }
  }
}

fun animation(from: String, to: String, consumer: Consumer<String>): Animation {
  return Animation(text(from, to), consumer)
}

private fun range(from: Int, to: Int): DoubleIntFunction {
  return DoubleIntFunction { value -> (from + value * (to - from)).toInt() }
}

private fun interface DoubleIntFunction {
  fun apply(value: Double): Int
}

class DoubleColorFunction(
  val from: Color,
  val to: Color
  ) : DoubleFunction<Color> {

  private val red = range(from.red, to.red)
  private val green = range(from.green, to.green)
  private val blue = range(from.blue, to.blue)
  private val alpha = range(from.alpha, to.alpha)

  override fun apply(value: Double) = Color(
    red.apply(value),
    green.apply(value),
    blue.apply(value),
    alpha.apply(value)
  )
}

class DoublePointFunction(
  val from: Point,
  val to: Point
) : DoubleFunction<Point> {

  private val x = range(from.x, to.x)
  private val y = range(from.y, to.y)

  override fun apply(value: Double): Point {
    return Point(x.apply(value), y.apply(value))
  }
}

class DoubleDimensionFunction(
  val from: Dimension,
  val to: Dimension
) : DoubleFunction<Dimension> {

  private val width = range(from.width, to.width)
  private val height = range(from.height, to.height)

  override fun apply(value: Double) = Dimension(
    width.apply(value),
    height.apply(value)
  )
}

class DoubleRectangleFunction(
  val from: Rectangle,
  val to: Rectangle
) : DoubleFunction<Rectangle> {

  private val x = range(from.x, to.x)
  private val y = range(from.y, to.y)
  private val width = range(from.width, to.width)
  private val height = range(from.height, to.height)

  override fun apply(value: Double) = Rectangle(
    x.apply(value),
    y.apply(value),
    width.apply(value),
    height.apply(value)
  )
}

/**
 * For any value in [0.0, 1.0] chooses value from an array.
 */
class DoubleArrayFunction<T>(
  val array: Array<T>
) : DoubleFunction<T> {
  override fun apply(value: Double): T {
    return array[(array.size * value).roundToInt().coerceIn(0, (array.size - 1))]
  }
}

fun interface FloatConsumer {
  fun accept(value: Float)
}