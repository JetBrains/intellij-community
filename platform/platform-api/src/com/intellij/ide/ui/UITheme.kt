// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.UIThemeBean.Companion.importFromParentTheme
import com.intellij.ide.ui.UIThemeBean.Companion.readTheme
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.Gray
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.ArrayUtilRt
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.io.InputStream
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.UIDefaults

private val LOG: Logger
  get() = logger<UITheme>()

/**
 * @author Konstantin Bulenkov
 */
class UITheme private constructor(val id: @NonNls String, private val bean: UIThemeBean) {
  val name: String?
    get() = bean.name

  val displayName: String?
    get() {
      if (bean.resourceBundle != null && bean.nameKey != null && bean.providerClassLoader != null) {
        val bundle = DynamicBundle.getResourceBundle(bean.providerClassLoader!!, bean.resourceBundle!!)
        return AbstractBundle.message(bundle, bean.nameKey!!)
      }
      return name
    }

  val isDark: Boolean
    get() = bean.dark

  val author: String?
    get() = bean.author

  val editorScheme: String?
    get() = bean.editorScheme
  val additionalEditorSchemes: List<String>
    get() = bean.additionalEditorSchemes ?: emptyList()
  val background: Map<String, Any?>
    get() = bean.background ?: emptyMap()
  val emptyFrameBackground: Map<String, Any?>
    get() = bean.emptyFrameBackground ?: emptyMap()

  fun applyProperties(defaults: UIDefaults) {
    val ui = bean.ui ?: return

    bean.colors?.let {
      loadColorPalette(defaults, it)
    }
    for ((key, value) in ui) {
      applyTheme(theme = bean, key = key, value = value, defaults = defaults)
    }
  }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var patcher: IconPathPatcher?
    get() = bean.patcher
    set(patcher) {
      bean.patcher = patcher
    }
  val colorPatcher: SvgElementColorPatcherProvider?
    get() = bean.colorPatcher
  val selectionColorPatcher: SvgElementColorPatcherProvider?
    get() = bean.selectionColorPatcher

  @get:ApiStatus.Internal
  val providerClassLoader: ClassLoader
    get() = bean.providerClassLoader ?: throw RuntimeException("The theme classloader has already been detached")

  @ApiStatus.Internal
  fun setProviderClassLoader(value: ClassLoader?) {
    bean.providerClassLoader = value
  }

  var editorSchemeName: String?
    get() = IdeUICustomization.getInstance().getUiThemeEditorSchemeName(id, bean.editorSchemeName)
    set(editorSchemeName) {
      bean.editorSchemeName = editorSchemeName
    }

  @Deprecated("Do not use.")
  fun setColors(colors: Map<String, Any?>?) {
    bean.colors = colors
  }

  companion object {
    const val FILE_EXT_ENDING: String = ".theme.json"

    @ApiStatus.Internal
    fun loadFromJson(stream: InputStream,
                     themeId: @NonNls String,
                     nameToParent: Function<String, UITheme?>): UITheme {
      val theme = readTheme(JsonFactory().createParser(stream))
      return UITheme(themeId,
                     postProcessTheme(theme = theme, parentTheme = findParentTheme(theme = theme, nameToParent = nameToParent)?.bean))
    }

    fun loadFromJson(data: ByteArray?,
                     themeId: @NonNls String,
                     provider: ClassLoader? = null,
                     iconMapper: ((String) -> String?)? = null): UITheme {
      val theme = readTheme(JsonFactory().createParser(data))
      return UITheme(themeId, postProcessTheme(theme = theme,
                                                                   parentTheme = findParentTheme(theme, ::oldFindThemeByName)?.bean,
                                                                   provider = provider,
                                                                   iconMapper = iconMapper))
    }

    fun loadFromJson(parentTheme: UITheme?,
                     data: ByteArray,
                     themeId: @NonNls String,
                     provider: ClassLoader?,
                     iconMapper: ((String) -> String?)? = null,
                     defaultDarkParent: Supplier<UITheme?>?,
                     defaultLightParent: Supplier<UITheme?>?): UITheme {
      val theme = readTheme(JsonFactory().createParser(data))
      return UITheme(id = themeId, if (parentTheme == null) {
        if (theme.dark) {
          postProcessTheme(theme = theme, parentTheme = defaultDarkParent?.get()?.bean, provider = provider, iconMapper = iconMapper)
        }
        else {
          postProcessTheme(theme = theme, parentTheme = defaultLightParent?.get()?.bean, provider = provider, iconMapper = iconMapper)
        }
      }
      else {
        postProcessTheme(theme = theme, parentTheme = parentTheme.bean, provider = provider, iconMapper = iconMapper)
      })
    }

    @TestOnly
    @JvmStatic
    fun getColorPalette(): Map<String, String?> = colorPalette
  }
}

private fun findParentTheme(theme: UIThemeBean, nameToParent: Function<String, UITheme?>): UITheme? {
  return nameToParent.apply(theme.parentTheme ?: return null)
}

private fun postProcessTheme(theme: UIThemeBean,
                             parentTheme: UIThemeBean?,
                             provider: ClassLoader? = null,
                             iconMapper: ((String) -> String?)? = null): UIThemeBean {
  if (parentTheme != null) {
    importFromParentTheme(theme, parentTheme)
  }
  if (provider != null) {
    theme.providerClassLoader = provider
  }
  initializeNamedColors(theme = theme)
  val paletteScopeManager = UiThemePaletteScopeManager()
  val colorsOnSelection = theme.iconColorsOnSelection
  if (!colorsOnSelection.isNullOrEmpty()) {
    val colors = HashMap<String, String>(colorsOnSelection.size)
    val alphaColors = HashSet<String>(colorsOnSelection.size)
    for ((key, value1) in colorsOnSelection) {
      val value = value1.toString()
      colors.put(key, value)
      alphaColors.add(value)
    }
    theme.selectionColorPatcher = object : SvgElementColorPatcherProvider {
      override fun attributeForPath(path: String): SvgAttributePatcher? {
        val scope = paletteScopeManager.getScopeByPath(path)
        val hash = InsecureHashBuilder()
          .stringMap(colors)
          .update(scope?.digest() ?: ArrayUtilRt.EMPTY_LONG_ARRAY)
        return newSvgPatcher(hash.build(), colors) { if (alphaColors.contains(it)) 255 else null }
      }
    }
  }

  val icons = theme.icons
  if (!icons.isNullOrEmpty()) {
    configureIcons(theme = theme, iconMapper = iconMapper, paletteScopeManager = paletteScopeManager, iconMap = icons)
  }

  return theme
}

private fun configureIcons(theme: UIThemeBean,
                           iconMapper: ((String) -> String?)?,
                           paletteScopeManager: UiThemePaletteScopeManager,
                           iconMap: Map<String, Any?>) {
  if (iconMapper != null) {
    theme.patcher = object : IconPathPatcher() {
      override fun patchPath(path: String, classLoader: ClassLoader?): String? {
        if (classLoader is PluginAwareClassLoader) {
          val icons = iconMap.get(classLoader.getPluginId().idString)
          if (icons is Map<*, *>) {
            val pluginIconPath = icons.get(path)
            if (pluginIconPath is String) {
              return iconMapper(pluginIconPath)
            }
          }
        }

        var value = iconMap.get(path)
        if (value == null && path[0] != '/') {
          value = iconMap.get("/$path")
        }
        return if (value is String) iconMapper(value) else null
      }

      override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?): ClassLoader? = theme.providerClassLoader
    }
  }

  val palette = iconMap.get("ColorPalette") as? Map<*, *> ?: return
  for (o in palette.keys) {
    val colorKey = o.toString()
    val scope = paletteScopeManager.getScope(colorKey) ?: continue
    val key = toColorString(key = colorKey, darkTheme = theme.dark)
    var v = palette.get(colorKey) as? String ?: continue
    val namedColor = theme.colors?.get(v)
    if (namedColor is String) {
      v = namedColor
    }

    var alpha: String? = null
    if (v.length == 9) {
      alpha = v.substring(7)
      v = v.substring(0, 7)
    }

    if (ColorHexUtil.fromHex(key, null) != null && ColorHexUtil.fromHex(v, null) != null) {
      scope.newPalette.put(key, v)
      var fillTransparency = -1
      if (alpha != null) {
        try {
          fillTransparency = alpha.toInt(16)
        }
        catch (ignore: Exception) {
        }
      }
      if (fillTransparency != -1) {
        scope.alphas.put(v, fillTransparency)
      }
    }
  }

  theme.colorPatcher = object : SvgElementColorPatcherProvider {
    override fun attributeForPath(path: String): SvgAttributePatcher? {
      val scope = paletteScopeManager.getScopeByPath(path) ?: return null
      return newSvgPatcher(digest = scope.digest(), newPalette = scope.newPalette) { scope.alphas.get(it) }
    }
  }
}

private fun initializeNamedColors(theme: UIThemeBean) {
  val map = LinkedHashMap(theme.colors ?: return)
  val namedColors = map.keys
  for (key in namedColors) {
    val value = map.get(key)
    if (value is String && !value.startsWith('#')) {
      val delegateColor = map.get(value)
      if (delegateColor != null) {
        map.put(key, delegateColor)
      }
      else {
        LOG.warn("Can't parse '$value' for key '$key'")
        map.put(key, Gray.TRANSPARENT)
      }
    }
  }

  var iconColorsOnSelection = theme.iconColorsOnSelection
  if (iconColorsOnSelection != null) {
    val entries = HashSet(iconColorsOnSelection.entries)
    iconColorsOnSelection = LinkedHashMap()
    theme.iconColorsOnSelection = iconColorsOnSelection
    for (entry in entries) {
      var key: Any? = entry.key
      var value: Any? = entry.value
      if (!key.toString().startsWith('#')) {
        key = map.get(key)
      }
      if (!value.toString().startsWith('#')) {
        value = map.get(value)
      }
      if (key.toString().startsWith('#') and value.toString().startsWith('#')) {
        iconColorsOnSelection.put(key.toString(), value)
      }
    }
  }
}

private fun toColorString(key: String, darkTheme: Boolean): String {
  if (darkTheme) {
    colorPalette.get("$key.Dark")?.let {
      return it.lowercase()
    }
  }
  return (colorPalette.get(key) ?: key).lowercase()
}

private val colorPalette: @NonNls Map<String, String> = java.util.Map.ofEntries(
  java.util.Map.entry("Actions.Red", "#DB5860"),
  java.util.Map.entry("Actions.Red.Dark", "#C75450"),
  java.util.Map.entry("Actions.Yellow", "#EDA200"),
  java.util.Map.entry("Actions.Yellow.Dark", "#F0A732"),
  java.util.Map.entry("Actions.Green", "#59A869"),
  java.util.Map.entry("Actions.Green.Dark", "#499C54"),
  java.util.Map.entry("Actions.Blue", "#389FD6"),
  java.util.Map.entry("Actions.Blue.Dark", "#3592C4"),
  java.util.Map.entry("Actions.Grey", "#6E6E6E"),
  java.util.Map.entry("Actions.Grey.Dark", "#AFB1B3"),
  java.util.Map.entry("Actions.GreyInline", "#7F8B91"),
  java.util.Map.entry("Actions.GreyInline.Dark", "#7F8B91"),
  java.util.Map.entry("Objects.Grey", "#9AA7B0"),
  java.util.Map.entry("Objects.Blue", "#40B6E0"),
  java.util.Map.entry("Objects.Green", "#62B543"),
  java.util.Map.entry("Objects.Yellow", "#F4AF3D"),
  java.util.Map.entry("Objects.YellowDark", "#D9A343"),
  java.util.Map.entry("Objects.Purple", "#B99BF8"),
  java.util.Map.entry("Objects.Pink", "#F98B9E"),
  java.util.Map.entry("Objects.Red", "#F26522"),
  java.util.Map.entry("Objects.RedStatus", "#E05555"),
  java.util.Map.entry("Objects.GreenAndroid", "#3DDC84"),
  java.util.Map.entry("Objects.BlackText", "#231F20"),
  java.util.Map.entry("Checkbox.Background.Default", "#FFFFFF"),
  java.util.Map.entry("Checkbox.Background.Default.Dark", "#43494A"),
  java.util.Map.entry("Checkbox.Background.Disabled", "#F2F2F2"),
  java.util.Map.entry("Checkbox.Background.Disabled.Dark", "#3C3F41"),
  java.util.Map.entry("Checkbox.Border.Default", "#b0b0b0"),
  java.util.Map.entry("Checkbox.Border.Default.Dark", "#6B6B6B"),
  java.util.Map.entry("Checkbox.Border.Disabled", "#BDBDBD"),
  java.util.Map.entry("Checkbox.Border.Disabled.Dark", "#545556"),
  java.util.Map.entry("Checkbox.Focus.Thin.Default", "#7B9FC7"),
  java.util.Map.entry("Checkbox.Focus.Thin.Default.Dark", "#466D94"),
  java.util.Map.entry("Checkbox.Focus.Wide", "#97C3F3"),
  java.util.Map.entry("Checkbox.Focus.Wide.Dark", "#3D6185"),
  java.util.Map.entry("Checkbox.Foreground.Disabled", "#ABABAB"),
  java.util.Map.entry("Checkbox.Foreground.Disabled.Dark", "#606060"),
  java.util.Map.entry("Checkbox.Background.Selected", "#4F9EE3"),
  java.util.Map.entry("Checkbox.Background.Selected.Dark", "#43494A"),
  java.util.Map.entry("Checkbox.Border.Selected", "#4B97D9"),
  java.util.Map.entry("Checkbox.Border.Selected.Dark", "#6B6B6B"),
  java.util.Map.entry("Checkbox.Foreground.Selected", "#FEFEFE"),
  java.util.Map.entry("Checkbox.Foreground.Selected.Dark", "#A7A7A7"),
  java.util.Map.entry("Checkbox.Focus.Thin.Selected", "#ACCFF7"),
  java.util.Map.entry("Checkbox.Focus.Thin.Selected.Dark", "#466D94"),
  java.util.Map.entry("Tree.iconColor", "#808080"),
  java.util.Map.entry("Tree.iconColor.Dark", "#AFB1B3")
)

private fun applyTheme(theme: UIThemeBean, key: String, value: Any?, defaults: UIDefaults) {
  @Suppress("NAME_SHADOWING")
  var value: Any? = value
  val valueStr = value.toString()
  var color: Color? = null
  if (theme.colors != null) {
    val obj = theme.colors!!.get(valueStr)
    if (obj != null) {
      color = parseColor(obj.toString())
      if (color != null && !key.startsWith('*')) {
        defaults.put(key, color)
        return
      }
    }
  }
  value = color ?: parseUiThemeValue(key = key, value = valueStr, classLoader = theme.providerClassLoader!!)
  if (key.startsWith("*.")) {
    val tail = key.substring(1)
    addPattern(key, value, defaults)
    for (k in defaults.keys.toTypedArray()) {
      if (k is String && k.endsWith(tail)) {
        defaults.put(k, value)
      }
    }
  }
  else {
    defaults.put(key, value)
  }
}

private fun addPattern(key: String?, value: Any?, defaults: UIDefaults) {
  var o = defaults.get("*")
  if (o !is Map<*, *>) {
    o = HashMap<String, Any?>()
    defaults.put("*", o)
  }
  @Suppress("UNCHECKED_CAST")
  val map = o as MutableMap<String, Any?>
  if (key != null && key.startsWith("*.")) {
    map.put(key.substring(2), value)
  }
}

private fun loadColorPalette(defaults: UIDefaults, colors: Map<String, Any?>) {
  for ((key, value) in colors) {
    val color = parseColor(value as? String ?: continue) ?: continue
    defaults.put("ColorPalette.$key", color)
  }
}

private fun oldFindThemeByName(parentTheme: String): UITheme? {
  for (laf in LafManager.getInstance().getInstalledLookAndFeels()) {
    if (laf is UIThemeLookAndFeelInfo) {
      val uiTheme = laf.theme
      if (uiTheme.name == parentTheme) {
        return uiTheme
      }
    }
  }
  return null
}

