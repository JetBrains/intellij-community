// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import java.awt.Color

private val LOG: Logger
  get() = logger<UITheme>()

internal class ColorMap {
  @JvmField
  var map: Map<String, Color> = emptyMap()

  @JvmField
  var rawMap: Map<String, ColorValue>? = null
}

internal sealed interface ColorValue

internal data class NamedColorValue(@JvmField val name: String) : ColorValue

internal data class AwtColorValue(@JvmField val color: Color) : ColorValue

internal fun readColorMapFromJson(parser: JsonParser,
                                  result: MutableMap<String, ColorValue>,
                                  warn: (String, Throwable?) -> Unit): MutableMap<String, ColorValue> {
  check(parser.currentToken() == JsonToken.START_OBJECT)

  l@
  while (true) {
    when (parser.nextToken()) {
      JsonToken.END_OBJECT -> {
        return result
      }
      JsonToken.VALUE_STRING -> {
        val text = parser.text
        val key = parser.currentName()
        if (isColorLike(text)) {
          val color = parseColorOrNull(text, key)
          if (color != null) {
            result.put(key, AwtColorValue(color))
            continue@l
          }
          warn("$key=$text has # prefix but cannot be parsed as color", null)
        }
        result.put(key, NamedColorValue(name = text))
      }
      JsonToken.FIELD_NAME -> {
      }
      null -> {
        break
      }
      JsonToken.START_OBJECT, JsonToken.START_ARRAY -> {
        logError(parser)
        parser.skipChildren()
      }
      else -> {
        logError(parser)
      }
    }
  }

  return result
}

private fun logError(parser: JsonParser) {
  logger<ColorMap>().warn("Not a color (" +
                          "token=${parser.currentToken}," +
                          "currentName=${parser.currentName}, " +
                          "currentValue=${parser.currentValue()}, " +
                          "currentLocation=${parser.currentLocation()}" +
                          ")")
}

internal fun initializeNamedColors(theme: UIThemeBean, warn: (String, Throwable?) -> Unit) {
  val rawColorMap = theme.colorMap.rawMap
  if (rawColorMap.isNullOrEmpty()) {
    theme.colorMap.map = emptyMap()
    return
  }

  // it is critically important to use our JB Color to apply theme on the fly - e.g., dark to light
  val colorMap = HashMap<String, Color>(rawColorMap.size)
  theme.colorMap.map = colorMap
  for ((key, value) in rawColorMap) {
    if (value is AwtColorValue) {
      colorMap.put(key, value.color)
      continue
    }

    val colorName = (value as NamedColorValue).name
    when (val color = rawColorMap.get(colorName)) {
      null -> {
        warn("Color $colorName is not mapped for key $key", null)
        colorMap.put(key, Gray.TRANSPARENT)
      }
      is AwtColorValue -> colorMap.put(key, color.color)
      else -> warn("Can't handle value $color for key '$key'", null)
    }
  }

  initColorOnSelectionMap(colorMap, theme)
}

private fun initColorOnSelectionMap(colorMap: Map<String, Color>, theme: UIThemeBean) {
  val rawIconColorOnSelectionMap = theme.iconColorOnSelectionMap.rawMap
  if (rawIconColorOnSelectionMap.isNullOrEmpty()) {
    theme.iconColorOnSelectionMap.map = emptyMap()
    return
  }

  val map = HashMap<String, Color>(rawIconColorOnSelectionMap.size)
  theme.iconColorOnSelectionMap.map = map

  for ((key, value) in rawIconColorOnSelectionMap) {
    if (value is AwtColorValue) {
      map.put(key, value.color)
      continue
    }

    val resolvedKey = if (key.startsWith('#')) {
      key
    }
    else {
      val color = colorMap.get(key)
      if (color == null) {
        LOG.error("Color by key $key is not defined")
        continue
      }
      "#" + ColorUtil.toHex(/* c = */ color, /* withAlpha = */ false)
    }

    val resolvedValue = colorMap.get((value as NamedColorValue).name)
    if (resolvedValue == null) {
      LOG.error("Color by key ${value.name} is not defined")
      continue
    }

    map.put(resolvedKey, resolvedValue)
  }
}