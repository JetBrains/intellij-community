// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.icons.ImageDataByPathLoader.Companion.findIconByPath
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.UIDefaults
import javax.swing.plaf.BorderUIResource
import javax.swing.plaf.ColorUIResource

fun parseUiThemeValue(key: String, value: String, classLoader: ClassLoader): Any? {
  try {
    return when {
      value == "null" -> null
      value == "true" -> true
      value == "false" -> false
      value.endsWith(".png") || value.endsWith(".svg") -> parseImageFile(value, classLoader)
      key.endsWith("Insets") || key.endsWith(".insets") || key.endsWith("padding") -> parseInsets(value)
      key.endsWith("Border") || key.endsWith("border") -> parseBorder(value, classLoader)
      key.endsWith("Size") -> parseSize(value)
      key.endsWith("Width") || key.endsWith("Height") -> getIntegerOrFloat(value, key)
      key.endsWith("grayFilter") -> parseGrayFilter(value)
      value.startsWith("AllIcons.") -> UIDefaults.LazyValue { getReflectiveIcon(value, classLoader) }
      !value.startsWith('#') && getIntegerOrFloat(value, null) != null -> getIntegerOrFloat(value, key)
      else -> parseElseConditions(value)
    }
  }
  catch (e: Exception) {
    logger<UITheme>().warn("Can't parse '$value' for key '$key'")
    return value
  }
}

private fun parseImageFile(value: String, classLoader: ClassLoader): Any {
  return UIDefaults.LazyValue { findIconByPath(path = value, classLoader = classLoader, cache = null, toolTip = null) }
}

private fun parseBorder(value: String, classLoader: ClassLoader): Any? {
  return try {
    val parsedValues = parseMultiValue(value).toList()
    when (parsedValues.size) {
      4 -> BorderUIResource.EmptyBorderUIResource(parseInsets(value))
      5 -> JBUI.asUIResource(JBUI.Borders.customLine(
        ColorHexUtil.fromHex(parsedValues[4]), parsedValues[0].toInt(), parsedValues[1].toInt(), parsedValues[2].toInt(),
        parsedValues[3].toInt()))
      else -> parseBorderColorOrBorderClass(value, classLoader)
    }
  }
  catch (e: Exception) {
    logger<UITheme>().warn(e)
    null
  }
}

private fun parseElseConditions(value: String): Any? {
  val color = parseColor(value)
  if (color != null) {
    return ColorUIResource(color)
  }

  val intVal = getInteger(value, null)
  if (intVal != null) {
    return intVal
  }
  return null
}

private fun parseBorderColorOrBorderClass(value: String, classLoader: ClassLoader): Any? {
  val color = ColorHexUtil.fromHexOrNull(value)
  if (color == null) {
    val aClass = classLoader.loadClass(value)
    val constructor = aClass.getDeclaredConstructor()
    constructor.isAccessible = true
    return constructor.newInstance()
  }
  else {
    return JBUI.asUIResource(JBUI.Borders.customLine(color, 1))
  }
}

internal fun parseColor(value: String): Color? {
  if (value.length == 8) {
    val color = ColorHexUtil.fromHex(value.substring(0, 6))
    try {
      val alpha = value.substring(6, 8).toInt(16)
      @Suppress("UseJBColor")
      return ColorUIResource(Color(color.red, color.green, color.blue, alpha))
    }
    catch (ignore: Exception) {
    }
    return null
  }

  val color = ColorHexUtil.fromHex(value, null)
  return if (color == null) null else ColorUIResource(color)
}

private fun parseMultiValue(value: String) = value.splitToSequence(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun getInteger(value: String, key: String?): Int? {
  try {
    return value.removeSuffix(".0").toInt()
  }
  catch (e: NumberFormatException) {
    if (key != null) {
      logger<UITheme>().warn("Can't parse: $key = $value")
    }
    return null
  }
}

private fun getIntegerOrFloat(value: String, key: String?): Number? {
  if (value.contains('.')) {
    try {
      return value.toFloat()
    }
    catch (e: NumberFormatException) {
      if (key != null) {
        logger<UITheme>().warn("Can't parse: $key = $value")
      }
      return null
    }
  }
  return getInteger(value, key)
}

private fun parseSize(value: String): Dimension {
  val numbers = parseMultiValue(value).iterator()
  return JBDimension(numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

private fun parseInsets(value: String): Insets {
  val numbers = parseMultiValue(value).iterator()
  return JBInsets(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

private fun parseGrayFilter(value: String): UIUtil.GrayFilter {
  val numbers = parseMultiValue(value).iterator()
  return UIUtil.GrayFilter(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}