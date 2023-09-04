// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ExperimentalUI
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import java.util.*
import java.util.function.BiFunction

internal class UIThemeBean {
  companion object {
    fun importFromParentTheme(theme: UIThemeBean, parentTheme: UIThemeBean) {
      theme.ui = importMapFromParentTheme(theme.ui, parentTheme.ui)
      theme.icons = importMapFromParentTheme(theme.icons, parentTheme.icons)
      theme.background = importMapFromParentTheme(theme.background, parentTheme.background)
      theme.emptyFrameBackground = importMapFromParentTheme(theme.emptyFrameBackground, parentTheme.emptyFrameBackground)
      theme.colors = importMapFromParentTheme(theme.colors, parentTheme.colors)
      theme.iconColorsOnSelection = importMapFromParentTheme(theme.iconColorsOnSelection, parentTheme.iconColorsOnSelection)
    }

    private fun importMapFromParentTheme(themeMap: MutableMap<String, Any?>?,
                                         parentThemeMap: Map<String, Any?>?): MutableMap<String, Any?>? {
      if (parentThemeMap == null) {
        return themeMap
      }

      val result = LinkedHashMap(parentThemeMap)
      if (themeMap != null) {
        for ((key, value) in themeMap) {
          result.remove(key)
          result.put(key, value)
        }
      }
      return result
    }

    fun readTheme(parser: JsonParser): UIThemeBean {
      check(parser.nextToken() == JsonToken.START_OBJECT)
      val bean = UIThemeBean()
      while (true) {
        when (parser.nextToken()) {
          JsonToken.START_OBJECT -> {
            val fieldName = parser.currentName()
            // ordered map is required (not clear why)
            val map = LinkedHashMap<String, Any?>()
            readFlatMapFromJson(parser, map)
            when (fieldName) {
              "icons" -> bean.icons = map
              "background" -> bean.background = map
              "emptyFrameBackground" -> bean.emptyFrameBackground = map
              "colors" -> bean.colors = map
              "iconColorsOnSelection" -> bean.iconColorsOnSelection = map
              "ui" -> {
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
          JsonToken.START_ARRAY -> {
            val fieldName = parser.currentName()
            val list = ArrayList<String>()
            while (parser.nextToken() != JsonToken.END_ARRAY) {
              when (parser.currentToken()) {
                JsonToken.VALUE_STRING -> {
                  list.add(parser.valueAsString)
                }
                else -> {}
              }
            }

            when (fieldName) {
              "additionalEditorSchemes" -> {
                bean.additionalEditorSchemes = list
              }
              else -> {
                logger<UIThemeBean>().warn("Unknown field: ${parser.currentName()}")
              }
            }
          }
          JsonToken.VALUE_STRING -> {
            when (parser.currentName()) {
              "id" -> bean.id = parser.valueAsString
              "name" -> bean.name = parser.valueAsString
              "nameKey" -> bean.nameKey = parser.valueAsString
              "parentTheme" -> bean.parentTheme = parser.valueAsString
              "resourceBundle" -> bean.resourceBundle = parser.valueAsString
              "author" -> bean.author = parser.valueAsString

              "editorScheme" -> bean.editorScheme = parser.valueAsString
              "editorSchemeName" -> bean.editorSchemeName = parser.valueAsString
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

    private fun readTopLevelBoolean(parser: JsonParser, bean: UIThemeBean, value: Boolean) {
      when (parser.currentName()) {
        "dark" -> bean.dark = value
      }
    }
  }

  @Transient
  @JvmField
  var providerClassLoader: ClassLoader? = null

  @JvmField
  var id: String? = null

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

  @JvmField
  var editorScheme: String? = null

  @JvmField
  var editorSchemeName: String? = null

  @JvmField
  var dark: Boolean = false

  @JvmField
  var additionalEditorSchemes: List<String>? = null

  @JvmField
  var ui: MutableMap<String, Any?>? = null

  @JvmField
  var icons: MutableMap<String, Any?>? = null

  @JvmField
  var background: MutableMap<String, Any?>? = null

  @JvmField
  var emptyFrameBackground: MutableMap<String, Any?>? = null

  @JvmField
  var colors: MutableMap<String, Any?>? = null

  @JvmField
  var iconColorsOnSelection: MutableMap<String, Any?>? = null

  @JvmField
  @Transient
  var patcher: IconPathPatcher? = null

  @JvmField
  @Transient
  var colorPatcher: SvgElementColorPatcherProvider? = null

  @JvmField
  @Transient
  var selectionColorPatcher: SvgElementColorPatcherProvider? = null
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
                prefix.joinTo(buffer = path, separator = "/")
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
        putEntry(prefix, result, parser, path) { parser.text }
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
                     getter: () -> Any?) {
  if (!prefix.isEmpty()) {
    prefix.joinTo(buffer = path, separator = ".")
  }

  val key = parser.currentName()
  val value = getter()
  when (key) {
    osKey -> {
    }
    OS_WINDOWS_KEY, OS_MACOS_KEY, OS_LINUX_KEY -> {
      path.setLength(0)
      return
    }
    OS_DEFAULT_KEY -> {
      val compositeKey = path.toString()
      path.setLength(0)

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

  result.put(path.toString(), value)
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

  ui.putIfAbsent("EditorTabs.underlineArc", "4")

  // require theme to specify ToolWindow stripe button colors explicitly, without "*"
  ui.putIfAbsent("ToolWindow.Button.selectedBackground", "#3573F0")
  ui.putIfAbsent("ToolWindow.Button.selectedForeground", "#FFFFFF")
}
