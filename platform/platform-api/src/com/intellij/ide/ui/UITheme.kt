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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.Gray
import com.intellij.ui.icons.ImageDataByPathLoader.Companion.findIconByPath
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.ArrayUtilRt
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.io.IOException
import java.io.InputStream
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.UIDefaults
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource
import javax.swing.plaf.ColorUIResource

private val LOG = logger<UITheme>()

/**
 * @author Konstantin Bulenkov
 */
class UITheme private constructor(private val bean: UIThemeBean) {
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

  val id: @NonNls String?
    get() = bean.id

  val editorScheme: String?
    get() = bean.editorScheme
  val additionalEditorSchemes: List<String>?
    get() = bean.additionalEditorSchemes
  val background: Map<String, Any?>?
    get() = bean.background
  val emptyFrameBackground: Map<String, Any?>?
    get() = bean.emptyFrameBackground

  fun applyProperties(defaults: UIDefaults) {
    if (bean.ui == null) {
      return
    }

    bean.colors?.let {
      loadColorPalette(defaults, it)
    }
    for ((key, value) in bean.ui!!) {
      apply(theme = bean, key = key, value = value, defaults = defaults)
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
    get() = bean.editorSchemeName
    set(editorSchemeName) {
      bean.editorSchemeName = editorSchemeName
    }

  internal class PaletteScope {
    val newPalette: MutableMap<String, String> = HashMap()
    val alphas: MutableMap<String, Int> = HashMap()
    private var hash: LongArray? = null

    fun digest(): LongArray {
      hash?.let {
        return it
      }

      // order is significant - use TreeMap
      hash = InsecureHashBuilder()
        .stringMap(newPalette)
        .stringIntMap(alphas)
        .build()
      return hash!!
    }
  }

  internal class PaletteScopeManager {
    val ui: PaletteScope = PaletteScope()
    val checkBoxes: PaletteScope = PaletteScope()
    val radioButtons: PaletteScope = PaletteScope()
    val trees: PaletteScope = PaletteScope()
    fun getScope(colorKey: String): PaletteScope? {
      if (colorKey.startsWith("Checkbox.")) return checkBoxes
      if (colorKey.startsWith("Radio.")) return radioButtons
      if (colorKey.startsWith("Tree.iconColor")) return trees
      if (colorKey.startsWith("Objects.")) return ui
      if (colorKey.startsWith("Actions.")) return ui
      if (colorKey.startsWith("#")) return ui
      LOG.warn("No color scope defined for key: $colorKey")
      return null
    }

    fun getScopeByPath(path: String?): PaletteScope? {
      if (path != null && (path.contains("com/intellij/ide/ui/laf/icons/") || path.contains("/com/intellij/ide/ui/laf/icons/"))) {
        val file = path.substring(path.lastIndexOf('/') + 1)
        if (file == "treeCollapsed.svg" || file == "treeExpanded.svg") return trees
        if (file.startsWith("check")) return checkBoxes
        if (file.startsWith("radio")) return checkBoxes //same set of colors as for checkboxes
        return null
      }
      return ui
    }
  }

  @Deprecated("Do not use.")
  fun setColors(colors: MutableMap<String, Any?>?) {
    bean.colors = colors
  }

  companion object {
    const val FILE_EXT_ENDING: String = ".theme.json"
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

    @ApiStatus.Internal
    @Throws(IOException::class)
    @JvmStatic
    fun loadFromJson(stream: InputStream,
                     themeId: @NonNls String,
                     nameToParent: Function<String, UITheme?>): UITheme {
      val theme = readTheme(JsonFactory().createParser(stream))
      theme.id = themeId
      return postProcessTheme(theme = theme, parentTheme = findParentTheme(theme = theme, nameToParent = nameToParent)?.bean)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun loadFromJson(data: ByteArray?,
                     themeId: @NonNls String,
                     provider: ClassLoader? = null,
                     iconMapper: ((String) -> String?)? = null): UITheme {
      val theme = readTheme(JsonFactory().createParser(data))
      theme.id = themeId
      return postProcessTheme(theme = theme,
                              parentTheme = findParentTheme(theme) { oldFindThemeByName(it) }?.bean,
                              provider = provider,
                              iconMapper = iconMapper)
    }

    fun loadFromJson(parentTheme: UITheme?,
                     data: ByteArray,
                     themeId: @NonNls String,
                     provider: ClassLoader?,
                     iconMapper: ((String) -> String?)? = null,
                     defaultDarkParent: Supplier<UITheme?>?,
                     defaultLightParent: Supplier<UITheme?>?): UITheme {
      val theme = readTheme(JsonFactory().createParser(data))
      theme.id = themeId
      return if (parentTheme == null) {
        if (theme.dark) {
          postProcessTheme(theme = theme, parentTheme = defaultDarkParent?.get()?.bean, provider = provider, iconMapper = iconMapper)
        }
        else {
          postProcessTheme(theme = theme, parentTheme = defaultLightParent?.get()?.bean, provider = provider, iconMapper = iconMapper)
        }
      }
      else {
        postProcessTheme(theme = theme, parentTheme = parentTheme.bean, provider = provider, iconMapper = iconMapper)
      }
    }

    private fun postProcessTheme(theme: UIThemeBean,
                                 parentTheme: UIThemeBean?,
                                 provider: ClassLoader? = null,
                                 iconMapper: ((String) -> String?)? = null): UITheme {
      if (parentTheme != null) {
        importFromParentTheme(theme, parentTheme)
      }
      return UITheme(loadFromJson(theme = theme, provider = provider, iconMapper = iconMapper))
    }

    @TestOnly
    @JvmStatic
    fun getColorPalette(): Map<String, String?> = colorPalette
  }
}

private fun findParentTheme(theme: UIThemeBean, nameToParent: Function<String, UITheme?>): UITheme? {
  return nameToParent.apply(theme.parentTheme ?: return null)
}

private fun loadFromJson(theme: UIThemeBean, provider: ClassLoader?, iconMapper: ((String) -> String?)?): UIThemeBean {
  if (provider != null) {
    theme.providerClassLoader = provider
  }

  initializeNamedColors(theme)
  val paletteScopeManager = UITheme.PaletteScopeManager()

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
      override fun attributeForPath(path: String?): SvgAttributePatcher? {
        val scope = paletteScopeManager.getScopeByPath(path)
        val hash = InsecureHashBuilder()
          .stringMap(colors)
          .update(scope?.digest() ?: ArrayUtilRt.EMPTY_LONG_ARRAY)
        return newSvgPatcher(hash.build(), colors) { if (alphaColors.contains(it)) 255 else null }
      }
    }
  }

  if (theme.icons != null && !theme.icons!!.isEmpty()) {
    theme.patcher = object : IconPathPatcher() {
      override fun patchPath(path: String, classLoader: ClassLoader?): String? {
        if (classLoader is PluginAwareClassLoader) {
          val pluginId = (classLoader as PluginAwareClassLoader).getPluginId().idString
          val icons = theme.icons!!.get(pluginId)
          if (icons is Map<*, *>) {
            val pluginIconPath = icons.get(path)
            if (pluginIconPath is String && iconMapper != null) {
              return iconMapper(pluginIconPath)
            }
          }
        }
        var value = theme.icons!![path]
        if (value == null && path[0] != '/') {
          value = theme.icons!!["/$path"]
        }
        return if (value is String && iconMapper != null) iconMapper(value) else null
      }

      override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?): ClassLoader? {
        return theme.providerClassLoader
      }
    }

    val palette = theme.icons!!.get("ColorPalette")
    if (palette is Map<*, *>) {
      for (o in palette.keys) {
        val colorKey = o.toString()
        val scope = paletteScopeManager.getScope(colorKey) ?: continue
        val key = toColorString(key = colorKey, darkTheme = theme.dark)
        var v: Any? = palette.get(colorKey)
        if (v is String) {
          val namedColor = if (theme.colors == null) null else theme.colors!![v]
          if (namedColor is String) {
            v = namedColor
          }
          var alpha: String? = null
          if (v.length == 9) {
            alpha = v.substring(7)
            v = v.substring(0, 7)
          }
          if (ColorHexUtil.fromHex(key, null) != null && ColorHexUtil.fromHex(v, null) != null) {
            scope.newPalette[key] = v
            var fillTransparency = -1
            if (alpha != null) {
              try {
                fillTransparency = alpha.toInt(16)
              }
              catch (ignore: Exception) {
              }
            }
            if (fillTransparency != -1) {
              scope.alphas[v] = fillTransparency
            }
          }
        }
      }
      theme.colorPatcher = object : SvgElementColorPatcherProvider {
        override fun attributeForPath(path: String?): SvgAttributePatcher? {
          val scope = paletteScopeManager.getScopeByPath(path)
          return if (scope == null) null else newSvgPatcher(scope.digest(), scope.newPalette) { scope.alphas.get(it) }
        }
      }
    }
  }
  return theme
}

private fun initializeNamedColors(theme: UIThemeBean) {
  val map = theme.colors ?: return
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
  if (theme.iconColorsOnSelection != null) {
    val entries = HashSet(theme.iconColorsOnSelection!!.entries)
    theme.iconColorsOnSelection!!.clear()
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
        theme.iconColorsOnSelection!!.put(key.toString(), value)
      }
    }
  }
}

private fun toColorString(key: String, darkTheme: Boolean): String {
  @Suppress("NAME_SHADOWING")
  var key = key
  if (darkTheme && colorPalette.get("$key.Dark") != null) {
    key += ".Dark"
  }
  return colorPalette.get(key)?.lowercase() ?: key.lowercase()
}

private val colorPalette: @NonNls MutableMap<String, String?> = java.util.Map.ofEntries(
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

private fun apply(theme: UIThemeBean, key: String, value: Any?, defaults: UIDefaults) {
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
        defaults[k] = value
      }
    }
  }
  else {
    defaults[key] = value
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

fun parseUiThemeValue(key: String, value: String, classLoader: ClassLoader): Any? {
  try {
    when (value) {
      "null" -> return null
      "true" -> return true
      "false" -> return false
    }

    when {
      value.endsWith(".png") || value.endsWith(".svg") -> {
        return UIDefaults.LazyValue { findIconByPath(path = value, classLoader = classLoader, cache = null, toolTip = null) }
      }
      key.endsWith("Insets") || key.endsWith(".insets") || key.endsWith("padding") -> {
        return parseInsets(value)
      }
      key.endsWith("Border") || key.endsWith("border") -> {
        try {
          val ints = parseMultiValue(value).toList()
          if (ints.size == 4) {
            return EmptyBorderUIResource(parseInsets(value))
          }
          else if (ints.size == 5) {
            return JBUI.asUIResource(JBUI.Borders.customLine(
              ColorHexUtil.fromHex(ints[4]), ints[0].toInt(), ints[1].toInt(), ints[2].toInt(), ints[3].toInt()))
          }
          val color = ColorHexUtil.fromHexOrNull(value)
          if (color == null) {
            val aClass = classLoader.loadClass(value)
            val constructor = aClass.getDeclaredConstructor()
            constructor.setAccessible(true)
            return constructor.newInstance()
          }
          else {
            return JBUI.asUIResource(JBUI.Borders.customLine(color, 1))
          }
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
      key.endsWith("Size") -> {
        return parseSize(value)
      }
      key.endsWith("Width") || key.endsWith("Height") -> {
        return getIntegerOrFloat(value, key)
      }
      key.endsWith("grayFilter") -> {
        return parseGrayFilter(value)
      }
      value.startsWith("AllIcons.") -> {
        return UIDefaults.LazyValue { getReflectiveIcon(value, classLoader) }
      }
      !value.startsWith('#') && getIntegerOrFloat(value, null) != null -> {
        return getIntegerOrFloat(value, key)
      }
      else -> {
        val color = parseColor(value)
        if (color != null) {
          return ColorUIResource(color)
        }
        val intVal = getInteger(value, null)
        if (intVal != null) {
          return intVal
        }
      }
    }
  }
  catch (e: Exception) {
    LOG.warn("Can't parse '$value' for key '$key'")
  }
  return value
}

private fun parseInsets(value: String): Insets {
  val numbers = parseMultiValue(value).iterator()
  return JBInsets(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

private fun parseGrayFilter(value: String): UIUtil.GrayFilter {
  val numbers = parseMultiValue(value).iterator()
  return UIUtil.GrayFilter(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

private fun parseColor(value: String?): Color? {
  @Suppress("NAME_SHADOWING")
  var value = value ?: return null
  if (value.startsWith('#')) {
    value = value.substring(1)
  }
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
      LOG.warn("Can't parse: $key = $value")
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
        LOG.warn("Can't parse: $key = $value")
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

private fun loadColorPalette(defaults: UIDefaults, colors: Map<String, Any?>) {
  for ((key, value) in colors) {
    val color = parseColor(value as? String ?: continue) ?: continue
    defaults.put("ColorPalette.$key", color)
  }
}