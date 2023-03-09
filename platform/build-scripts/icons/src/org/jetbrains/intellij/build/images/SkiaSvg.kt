// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.images

import org.jetbrains.skia.*
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit
import org.jsoup.parser.ParseSettings
import org.jsoup.parser.Parser
import org.jsoup.select.QueryParser
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function


internal fun createSvgDom(file: Path): SVGDOM = SVGDOM(Data.makeFromFileName(file.toString()))

internal fun renderSvgUsingSkia(file: Path, scale: Float): Bitmap = renderSvgUsingSkia(svg = createSvgDom(file), scale)

internal fun renderSvgUsingSkia(svg: SVGDOM, scale: Float): Bitmap {
  val root = svg.root!!
  val width = svgLengthToPixel(root.width)
  val height = svgLengthToPixel(root.height)
  check(width > 0)
  check(height > 0)
  svg.setContainerSize(width, height)

  val bmp = Bitmap()
  bmp.allocPixels(org.jetbrains.skia.ImageInfo.makeS32((width * scale).toInt(), (height * scale).toInt(), ColorAlphaType.UNPREMUL))
  val canvas = Canvas(bmp)
  canvas.scale(scale, scale)
  svg.render(canvas)

  check(bmp.width > 0)
  check(bmp.height > 0)
  return bmp
}

private fun svgLengthToPixel(root: SVGLength): Float {
  return when (root.unit) {
    SVGLengthUnit.PERCENTAGE -> (root.value * 16f) / 100f
    SVGLengthUnit.NUMBER, SVGLengthUnit.PX -> root.value
    else -> {
      throw UnsupportedOperationException(root.toString())
    }
  }
}

private val STYLE by lazy { QueryParser.parse("style") }
private val STYLE_PRIORITY = Priority(1, 0, 0, 0)
private val parseSettings = ParseSettings(true, true)

private val PRIORITY_CACHE = ConcurrentHashMap<String, Priority>()

internal fun inlineSvgStyles(xml: String): ByteArray {
  if (!xml.contains("class=")) {
    return xml.encodeToByteArray()
  }

  val parser = Parser.htmlParser()
  parser.settings(parseSettings)

  //val t1 = System.nanoTime()
  val document = parser.parseInput(xml, "")
  val styleTags = document.select(STYLE)
  if (styleTags.isEmpty()) {
    return xml.encodeToByteArray()
  }

  // this map stores all the styles we need to apply to the elements
  val stylesToApply = HashMap<String, Map<String, ValueWithPriority>>()
  for (style in styleTags) {
    val rules = style.data()
      // remove newlines
      .replace("\n", "")
      // remove comments
      .replace("\\/\\*[^*]*\\*+([^/*][^*]*\\*+)*\\/", "")
    val statements = rules.splitToSequence('{', '}').map(String::trim).filterNot(String::isEmpty).iterator()
    while (statements.hasNext()) {
      val selector = statements.next()
      // the list of css styles for the selector
      assert(statements.hasNext())
      val properties = statements.next()
      val splitSelectors = selector.splitToSequence(',').map(String::trim).filterNot(String::isEmpty)
      for (splitSelector in splitSelectors) {
        val selectedElements = document.select(splitSelector)
        for (selectorElement in selectedElements) {
          if (selectorElement.equals(style)) {
            continue
          }

          val exactSelector = selectorElement.cssSelector()
          val existingStyles = stylesToApply.get(exactSelector) ?: stylesOf(STYLE_PRIORITY, selectorElement.attr("style"))
          stylesToApply.put(exactSelector, mergeStyle(
            oldProps = existingStyles,
            newProps = stylesOf(getPriority(splitSelector), properties)
          ))
        }
      }
    }
    style.remove()
  }

  // apply the styles
  for ((exactSelector, styles) in stylesToApply) {
    for (element in document.select(exactSelector)) {
      element.removeAttr("class")
      for ((property, v) in styles) {
        element.attr(property, v.value)
      }
    }
  }
  //val t2 = System.nanoTime()
  //println("Spent " + (t2 - t1) / 1000L + " milliseconds inlining CSS")
  return document.body().html().encodeToByteArray()
}

private fun getPriority(selector: String): Priority {
  return PRIORITY_CACHE.computeIfAbsent(selector, Function {
    var b = 0
    var c = 0
    var d = 0
    val pieces = selector.splitToSequence(' ').map(String::trim).filter { !it.isEmpty() }
    for (pc in pieces) {
      if (pc.startsWith("#")) {
        b++
        continue
      }
      if (pc.contains('[') || pc.startsWith('.') || pc.contains(':') && !pc.contains("::")) {
        c++
        continue
      }
      d++
    }
    Priority(a = 0, b = b, c = c, d = d)
  })
}

private fun stylesOf(priority: Priority, properties: String?): Map<String, ValueWithPriority?> {
  if (properties.isNullOrBlank()) {
    return emptyMap()
  }

  val props = properties.split(';')
  if (props.isEmpty()) {
    return emptyMap()
  }

  val result = HashMap<String, ValueWithPriority>()
  for (p in props) {
    val pcs = p.split(':')
    if (pcs.size != 2) {
      continue
    }

    val name = pcs[0].trim()
    val value = pcs[1].trim()
    result.put(name, ValueWithPriority(priority = priority, value = value))
  }
  return result
}

private fun mergeStyle(oldProps: Map<String, ValueWithPriority?>,
                       newProps: Map<String, ValueWithPriority?>): Map<String, ValueWithPriority> {
  val result = LinkedHashMap<String, ValueWithPriority>()
  val allProps = LinkedHashSet<String>(oldProps.size + newProps.size)
  allProps.addAll(oldProps.keys)
  allProps.addAll(newProps.keys)
  for (p in allProps) {
    val oldValue = oldProps.get(p)
    val newValue = newProps.get(p)
    if (oldValue == null) {
      result.put(p, newValue!!)
      continue
    }
    if (newValue == null) {
      result.put(p, oldValue)
      continue
    }

    val compare = oldValue.priority.compareTo(newValue.priority)
    result.put(p, if (compare < 0) newValue else oldValue)
  }
  return result
}

private data class Priority(@JvmField var a: Int, @JvmField var b: Int, @JvmField var c: Int, @JvmField var d: Int) : Comparable<Priority> {
  override operator fun compareTo(other: Priority): Int {
    if (other === this) {
      return 0
    }

    var result = a.compareTo(other.a)
    if (result != 0) {
      return result
    }

    result = b.compareTo(other.b)
    if (result != 0) {
      return result
    }

    result = c.compareTo(other.c)
    return if (result == 0) d.compareTo(other.d) else result
  }
}

private class ValueWithPriority(@JvmField val value: String, @JvmField val priority: Priority)