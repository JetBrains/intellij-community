// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "SSBasedInspection")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ExperimentalUI
import java.util.*
import java.util.function.BiFunction

internal class UIThemeBean {
  @JvmField
  var name: String? = null

  @JvmField
  var nameKey: String? = null

  @JvmField
  var parentTheme: String? = null

  @JvmField
  var resourceBundle: String? = "messages.IdeBundle"

  @JvmField
  var author: String? = null

  /**
   * The path to editor scheme file.
   */
  @JvmField
  var editorScheme: String? = null

  @JvmField
  var dark: Boolean = false

  @JvmField
  var ui: Map<String, Any?>? = null

  @JvmField
  var icons: Map<String, Any?>? = null

  @JvmField
  var background: Map<String, Any?>? = null

  @JvmField
  var emptyFrameBackground: Map<String, Any?>? = null

  @JvmField
  var colorMap: ColorMap = ColorMap()
  @JvmField
  var iconColorOnSelectionMap: ColorMap = ColorMap()

  override fun toString() = "UIThemeBean(name=$name, parentTheme=$parentTheme, dark=$dark)"
}

internal fun readTheme(parser: JsonParser): UIThemeBean {
  check(parser.nextToken() == JsonToken.START_OBJECT)
  val bean = UIThemeBean()
  while (true) {
    when (parser.nextToken()) {
      JsonToken.START_OBJECT -> {
        when (val fieldName = parser.currentName()) {
          "icons" -> bean.icons = readMapFromJson(parser)
          "background" -> bean.background = readMapFromJson(parser)
          "emptyFrameBackground" -> bean.emptyFrameBackground = readMapFromJson(parser)
          "colors" -> bean.colorMap.rawMap = readColorMapFromJson(parser, HashMap())
          "iconColorsOnSelection" -> bean.iconColorOnSelectionMap.rawMap = readColorMapFromJson(parser, HashMap())
          "ui" -> {
            // ordered map is required (not clear why)
            val map = LinkedHashMap<String, Any?>(700)
            readFlatMapFromJson(parser, map)
            putDefaultsIfAbsent(map)
            bean.ui = map
          }
          "UIDesigner" -> {
            parser.skipChildren()
          }
          else -> {
            logger<UIThemeBean>().warn("Unknown field: $fieldName")
          }
        }
      }
      JsonToken.END_OBJECT -> {
      }
      JsonToken.VALUE_STRING -> {
        when (parser.currentName()) {
          "id" -> {
            logger<UIThemeBean>().warn("Do not set theme id in JSON (value=${parser.valueAsString})")
          }
          "name" -> bean.name = parser.valueAsString
          "nameKey" -> bean.nameKey = parser.valueAsString
          "parentTheme" -> bean.parentTheme = parser.valueAsString
          "resourceBundle" -> bean.resourceBundle = parser.valueAsString
          "author" -> bean.author = parser.valueAsString

          "editorScheme" -> bean.editorScheme = parser.valueAsString
        }
      }
      JsonToken.VALUE_TRUE -> readTopLevelBoolean(parser, bean, value = true)
      JsonToken.VALUE_FALSE -> readTopLevelBoolean(parser, bean, value = false)
      JsonToken.FIELD_NAME -> {
      }
      null -> break
      else -> {
        logger<UIThemeBean>().warn("Unknown field: ${parser.currentName()}")
      }
    }
  }

  putDefaultsIfAbsent(bean)
  return bean
}

/**
 * Flatten example: `"Editor": { "SearchField": { "borderInsets": "7,10,7,8" } }` is flattened to
 * `"Editor.SearchField.borderInsets": "7,10,7,8` in internal representation.
 *
 * Per-OS keys are also resolved as shown below:
 * ```json
 *  "Menu.borderColor": {
 *    "os.default": "Grey12",
 *    "os.windows": "Blue12"
 *  }
 *  ```
 *
 * This is useful when we need to validate if a certain key was already set in [putDefaultsIfAbsent],
 * and to uniformly override parentTheme keys regardless of used format.
 *
 * Note: we intentionally do not expand "*" patterns here.
 */
private fun readFlatMapFromJson(parser: JsonParser, result: MutableMap<String, Any?>) {
  check(parser.currentToken() == JsonToken.START_OBJECT)

  val prefix = ArrayDeque<String>()
  val path = StringBuilder()
  var currentFieldName: String? = null
  var level = 1
  while (true) {
    when (parser.nextToken()) {
      JsonToken.START_OBJECT -> {
        level++
        prefix.addLast(currentFieldName!!)
        currentFieldName = null
      }
      JsonToken.END_OBJECT -> {
        level--
        prefix.pollLast()
        currentFieldName = null

        if (level == 0) {
          assert(prefix.isEmpty())
          break
        }
      }
      JsonToken.START_ARRAY -> {
        val fieldName = parser.currentName()
        while (true) {
          when (parser.nextToken()) {
            JsonToken.END_ARRAY -> break
            JsonToken.VALUE_STRING -> {
              if (!prefix.isEmpty()) {
                prefix.joinTo(buffer = path, separator = ".")
                path.append('.')
              }
              path.append(fieldName)
              result.put(path.toString(), parser.text)
              path.setLength(0)
            }
            else -> {
              logError(parser)
            }
          }
        }
      }
      JsonToken.VALUE_STRING -> {
        putEntry(prefix, result, parser, path) { parseStringValue(value = parser.text, key = it) }
      }
      JsonToken.VALUE_NUMBER_INT -> {
        putEntry(prefix, result, parser, path) { parser.intValue }
      }
      JsonToken.VALUE_NUMBER_FLOAT -> {
        putEntry(prefix, result, parser, path) { parser.doubleValue }
      }
      JsonToken.VALUE_FALSE -> {
        putEntry(prefix, result, parser, path) { false }
      }
      JsonToken.VALUE_TRUE -> {
        putEntry(prefix, result, parser, path) { true }
      }
      JsonToken.VALUE_NULL -> {
      }
      JsonToken.FIELD_NAME -> {
        currentFieldName = parser.currentName()
      }
      null -> {
        break
      }
      else -> {
        logError(parser)
      }
    }
  }

  result.replaceAll(BiFunction { _, u -> if (u is OsDefaultValue) u.v else u })
}

private fun readMapFromJson(parser: JsonParser): MutableMap<String, Any?> {
  val m = LinkedHashMap<String, Any?>()
  readMapFromJson(parser, m)
  return m
}

private fun readMapFromJson(parser: JsonParser, result: MutableMap<String, Any?>) {
  check(parser.currentToken() == JsonToken.START_OBJECT)

  var level = 1
  l@
  while (true) {
    when (parser.nextToken()) {
      JsonToken.START_OBJECT -> {
        level++
        val m = LinkedHashMap<String, Any?>()
        result.put(parser.currentName(), m)
        readMapFromJson(parser, m)
      }
      JsonToken.END_OBJECT -> {
        level--

        if (level == 0) {
          break
        }
      }
      JsonToken.VALUE_STRING -> {
        val text = parser.text
        val key = parser.currentName()
        if (isColorLike(text)) {
          val color = parseColorOrNull(text, key)
          if (color != null) {
            result.put(key, createColorResource(color, key))
            continue@l
          }
          logger<UITheme>().warn("$key=$text has # prefix but cannot be parsed as color")
        }
        result.put(key, text)
      }
      JsonToken.VALUE_NUMBER_INT -> {
        result.put(parser.currentName(), parser.intValue)
      }
      JsonToken.VALUE_NUMBER_FLOAT -> {
        result.put(parser.currentName(), parser.doubleValue)
      }
      JsonToken.VALUE_FALSE -> {
        result.put(parser.currentName(), false)
      }
      JsonToken.VALUE_TRUE -> {
        result.put(parser.currentName(), true)
      }
      JsonToken.VALUE_NULL -> {
      }
      JsonToken.FIELD_NAME -> {
      }
      null -> {
        break
      }
      else -> {
        logError(parser)
      }
    }
  }
}

private const val OS_MACOS_KEY = "os.mac"
private const val OS_WINDOWS_KEY = "os.windows"
private const val OS_LINUX_KEY = "os.linux"
private const val OS_DEFAULT_KEY = "os.default"

private val osKey = when {
  SystemInfoRt.isWindows -> OS_WINDOWS_KEY
  SystemInfoRt.isMac -> OS_MACOS_KEY
  else -> OS_LINUX_KEY
}

private fun putEntry(prefix: Deque<String>,
                     result: MutableMap<String, Any?>,
                     parser: JsonParser,
                     path: StringBuilder,
                     getter: (key: String) -> Any?) {
  if (!prefix.isEmpty()) {
    var isFirst = true
    for (element in prefix) {
      if (isFirst) {
        isFirst = false
      }
      else if (element != "UI") {
        path.append('.')
      }
      path.append(element)
    }
  }

  when (val key = parser.currentName()) {
    osKey -> {
    }
    OS_WINDOWS_KEY, OS_MACOS_KEY, OS_LINUX_KEY -> {
      path.setLength(0)
      return
    }
    OS_DEFAULT_KEY -> {
      val compositeKey = path.toString()
      path.setLength(0)

      val value = getter(compositeKey)
      val oldValue = result.putIfAbsent(compositeKey, OsDefaultValue(value))
      if (oldValue is OsDefaultValue) {
        logger<UIThemeBean>().error("Duplicated value: (value=$value, compositeKey=$compositeKey)")
      }
      return
    }
    "UI" -> {
      path.append(key)
    }
    else -> {
      if (path.isNotEmpty()) {
        path.append('.')
      }
      path.append(key)
    }
  }

  val finalKey = path.toString()
  val value = getter(finalKey)
  result.put(finalKey, value)
  path.setLength(0)
}

private class OsDefaultValue(@JvmField val v: Any?)

private fun logError(parser: JsonParser) {
  logger<UIThemeBean>().warn("JSON contains data in unsupported format (token=${parser.currentToken}): ${parser.currentValue()}")
}

/**
 * Ensure that the old themes are not missing some vital keys.
 *
 * We are patching them here instead of using [com.intellij.ui.JBColor.namedColor] fallback
 * to make sure [javax.swing.UIManager.getColor] works properly.
 */
private fun putDefaultsIfAbsent(theme: UIThemeBean) {
  if (!ExperimentalUI.isNewUI()) {
    return
  }

  var ui = theme.ui
  if (ui == null) {
    ui = LinkedHashMap()
    theme.ui = ui
    putDefaultsIfAbsent(ui)
  }
}

private fun putDefaultsIfAbsent(ui: MutableMap<String, Any?>) {
  if (!ExperimentalUI.isNewUI()) {
    return
  }

  ui.putIfAbsent("EditorTabs.underlineArc", 4)

  // require theme to specify ToolWindow stripe button colors explicitly, without "*"
  ui.putIfAbsent("ToolWindow.Button.selectedBackground", "#3573F0")
  ui.putIfAbsent("ToolWindow.Button.selectedForeground", "#FFFFFF")
}

private fun readTopLevelBoolean(parser: JsonParser, bean: UIThemeBean, value: Boolean) {
  when (parser.currentName()) {
    "dark" -> bean.dark = value
  }
}

internal fun importFromParentTheme(theme: UIThemeBean, parentTheme: UIThemeBean) {
  theme.ui = importMapFromParentTheme(theme.ui, parentTheme.ui)
  theme.icons = importMapFromParentTheme(theme.icons, parentTheme.icons)
  theme.background = importMapFromParentTheme(theme.background, parentTheme.background)
  theme.emptyFrameBackground = importMapFromParentTheme(theme.emptyFrameBackground, parentTheme.emptyFrameBackground)
  theme.colorMap.rawMap = importMapFromParentTheme(theme.colorMap.rawMap, parentTheme.colorMap.rawMap)
  theme.iconColorOnSelectionMap.rawMap = importMapFromParentTheme(theme.iconColorOnSelectionMap.rawMap, parentTheme.iconColorOnSelectionMap.rawMap)
}

@Suppress("SSBasedInspection")
private fun <T : Any?> importMapFromParentTheme(map: Map<String, T>?, parentMap: Map<String, T>?): Map<String, T>? {
  if (parentMap == null) {
    return map
  }
  if (map == null) {
    return LinkedHashMap(parentMap)
  }

  val result = LinkedHashMap<String, T>(parentMap.size + map.size)
  for (entry in parentMap.entries) {
    if (entry.key !in map) {
      result.put(entry.key, entry.value)
    }
  }
  result.putAll(map)
  return result
}