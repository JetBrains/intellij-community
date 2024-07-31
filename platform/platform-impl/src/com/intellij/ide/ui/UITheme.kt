// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "SSBasedInspection")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.laf.UIThemeExportableBean
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoImpl
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.swing.UIDefaults
import javax.swing.plaf.UIResource

/**
 * @author Konstantin Bulenkov
 */
class UITheme internal constructor(
  val id: @NonNls String,
  private val bean: UIThemeBean,
  val colorPatcher: SvgElementColorPatcherProvider?,
  @JvmField internal val selectionColorPatcher: SvgElementColorPatcherProvider?,
  @JvmField internal var patcher: IconPathPatcher?,
  private var _providerClassLoader: ClassLoader? = null,
) {
  val name: String?
    get() = bean.name

  val displayName: String?
    get() {
      val classLoader = _providerClassLoader
      if (bean.resourceBundle != null && bean.nameKey != null && classLoader != null) {
        val bundle = DynamicBundle.getResourceBundle(classLoader, bean.resourceBundle!!)
        return AbstractBundle.message(bundle, bean.nameKey!!)
      }
      return name
    }

  val isDark: Boolean
    get() = bean.dark

  val author: String?
    get() = bean.author

  val background: Map<String, Any?>
    get() = bean.background ?: emptyMap()
  val emptyFrameBackground: Map<String, Any?>
    get() = bean.emptyFrameBackground ?: emptyMap()

  fun applyTheme(defaults: UIDefaults) {
    for (entry in defaults.entries) {
      val value = entry.value
      if (value is Color && !(value is JBColor && value.name != null)) {
        val key = entry.key.toString()
        entry.setValue(IJColorUIResource(value, key))
      }
    }

    val classLoader = providerClassLoader
    val warn = createWarnFunction(classLoader)

    for ((key, value) in bean.colorMap.map) {
      val k = "ColorPalette.$key"
      assert(value !is IJColorUIResource)
      defaults.put(k, IJColorUIResource(color = value, name = k))
    }

    val colors = bean.colorMap.map.takeIf { it.isNotEmpty() }
    for ((key, value) in (bean.ui ?: return)) {
      var color: Color? = null
      if (colors != null && value is String) {
        colors.get(value)?.let {
          color = it
        }
      }

      @Suppress("NAME_SHADOWING")
      val value = color ?: parseUiThemeValue(key = key, value = value, classLoader = classLoader, warn = warn)
      if (key.startsWith("*.")) {
        val tail = key.substring(1)
        addPattern(key, value, defaults)
        for (k in defaults.keys.toTypedArray()) {
          if (k is String && k.endsWith(tail)) {
            defaults.put(k, transformToIJColorUIResourceIfNeeded(value = value, key = k))
          }
        }
      }
      else {
        defaults.put(key, transformToIJColorUIResourceIfNeeded(value = value, key = key))
      }
    }

    for (entry in defaults.entries) {
      val value = entry.value
      if (value is Color && !(value is JBColor && value.name != null)) {
        thisLogger().warn("${entry.key} is not preprocessed")
      }
    }
  }

  private fun transformToIJColorUIResourceIfNeeded(value: Any?, key: String): Any? {
    return if (value is Color && !(value is JBColor && value.name != null)) {
      IJColorUIResource(value, key)
    }
    else {
      value
    }
  }

  @get:ApiStatus.Internal
  val providerClassLoader: ClassLoader
    get() = _providerClassLoader ?: throw RuntimeException("The theme classloader has already been detached")

  @ApiStatus.Internal
  fun setProviderClassLoader(value: ClassLoader?) {
    _providerClassLoader = value
  }

  internal fun describe(): UIThemeExportableBean {
    val iconMap = bean.icons?.let {
      val iconMap = LinkedHashMap<String, String>(it.size)
      for ((key, value) in it) {
        if (key != "ColorPalette" && value is String) {
          iconMap.put(key, value)
        }
      }
      iconMap
    } ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    return UIThemeExportableBean(
      colors = bean.colorMap.map.mapValues { it.value.rgb },
      iconColorsOnSelection = bean.iconColorOnSelectionMap.map.mapValues { it.value.rgb },
      icons = iconMap,
      colorPalette = bean.icons?.get("ColorPalette") as? Map<String, String> ?: emptyMap()
    )
  }

  var editorSchemeId: String? = bean.editorScheme
    // custom set to be able to set a fixed editor scheme id (see the only setter in EditorColorsManagerImpl)
    internal set

  internal val originalEditorSchemeId: String?
    get() = bean.editorScheme

  companion object {
    const val FILE_EXT_ENDING: String = ".theme.json"

    @ApiStatus.Internal
    fun loadTempThemeFromJson(stream: InputStream, themeId: @NonNls String): UITheme {
      val classLoader = UITheme::class.java.classLoader
      val warn = createWarnFunction(classLoader)
      val theme = readTheme(JsonFactory().createParser(stream), warn)
      return createTheme(themeId = themeId,
                         theme = theme,
                         parentTheme = resolveParentTheme(theme, themeId),
                         classLoader = classLoader,
                         warn = warn)
    }

    fun loadFromJson(data: ByteArray,
                     themeId: @NonNls String,
                     classLoader: ClassLoader,
                     iconMapper: ((String) -> String?)? = null): UITheme {
      val warn = createWarnFunction(classLoader)
      val theme = readTheme(JsonFactory().createParser(data), warn)
      val parentTheme = resolveParentTheme(theme, themeId)
      return createTheme(theme = theme,
                         parentTheme = parentTheme,
                         classLoader = classLoader,
                         iconMapper = iconMapper,
                         themeId = themeId,
                         warn = warn)
    }

    internal fun loadDeprecatedFromJson(data: ByteArray, themeId: @NonNls String, classLoader: ClassLoader): UITheme {
      val warn = createWarnFunction(classLoader)
      val theme = readTheme(JsonFactory().createParser(data), warn)
      return createTheme(theme = theme, parentTheme = null, classLoader = classLoader, iconMapper = null, themeId = themeId, warn = warn)
    }

    /**
     * Returns true if [theme] is based on experimental theme Dark or Light
     */
    internal fun isBasedOnExperimentalTheme(theme: UIThemeBean): Boolean {
      var current: UIThemeBean? = theme
      while (current != null) {
        if (current.name == "Light" || current.name == "Dark") {
          return true
        }
        val parentTheme = current.parentTheme?: return false
        current = (UiThemeProviderListManager.getInstance().findThemeById(parentTheme) as? UIThemeLookAndFeelInfoImpl)?.theme?.bean
      }
      return false
    }

    private fun resolveParentTheme(theme: UIThemeBean, themeId: @NonNls String): UIThemeBean? {
      val parentThemeId = theme.parentTheme
      if (parentThemeId == null) {
        return UiThemeProviderListManager.getInstance().findDefaultParent(isDark = theme.dark, themeId = themeId)?.bean
      }
      else {
        return parentThemeId.let {
          (UiThemeProviderListManager.getInstance().findThemeById(it) as? UIThemeLookAndFeelInfoImpl)?.theme
        }?.bean
      }
    }

    internal fun loadFromJson(parentTheme: UITheme?,
                              data: ByteArray,
                              themeId: @NonNls String,
                              classLoader: ClassLoader,
                              iconMapper: ((String) -> String?)? = null,
                              defaultDarkParent: Supplier<UITheme?>?,
                              defaultLightParent: Supplier<UITheme?>?,
                              warn: (String, Throwable?) -> Unit): UITheme {
      val bean = readTheme(JsonFactory().createParser(data), warn)
      val parent: UIThemeBean?
      if (parentTheme == null) {
        val parentThemeId = bean.parentTheme
        if (parentThemeId == null) {
          parent = (if (bean.dark) defaultDarkParent?.get()?.bean else defaultLightParent?.get()?.bean)
        }
        else {
          parent = (UiThemeProviderListManager.getInstance().findThemeById(parentThemeId) as? UIThemeLookAndFeelInfoImpl)?.theme?.bean
        }
      }
      else {
        parent = parentTheme.bean
        bean.parentTheme = parentTheme.id
      }
      return createTheme(theme = bean,
                         parentTheme = parent,
                         classLoader = classLoader,
                         iconMapper = iconMapper,
                         themeId = themeId,
                         warn = warn)
    }

    @TestOnly
    @JvmStatic
    fun getColorPalette(): Map<String, String?> = colorPaletteClassic
  }
}

private fun createTheme(theme: UIThemeBean,
                        parentTheme: UIThemeBean?,
                        classLoader: ClassLoader,
                        iconMapper: ((String) -> String?)? = null,
                        themeId: @NonNls String,
                        warn: (String, Throwable?) -> Unit): UITheme {
  if (parentTheme != null) {
    importFromParentTheme(theme, parentTheme)
  }
  initializeNamedColors(theme, warn = warn)

  val paletteScopeManager = UiThemePaletteScopeManager(theme)

  val iconMap = theme.icons
  var colorPatcher: SvgElementColorPatcherProvider? = null

  var patcher: IconPathPatcher? = null
  if (!iconMap.isNullOrEmpty()) {
    patcher = object : IconPathPatcher() {
      private val iconMapper = iconMapper ?: { it }

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

      override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?): ClassLoader = classLoader
    }

    colorPatcher = paletteScopeManager.configureIcons(theme = theme, iconMap = iconMap)
  }

  val colorsOnSelection = theme.iconColorOnSelectionMap.map
  var selectionColorPatcher: SvgElementColorPatcherProvider? = null
  if (!colorsOnSelection.isEmpty()) {
    val colors = HashMap<String, String>(colorsOnSelection.size)
    val alphaColors = HashSet<String>(colorsOnSelection.size)
    for ((key, v) in colorsOnSelection) {
      val value = "#" + ColorUtil.toHex(/* c = */ v, /* withAlpha = */ false)
      colors.put(key, value)
      alphaColors.add(value)
    }

    val digest = paletteScopeManager.computeDigest(InsecureHashBuilder())
      .putStringMap(colors)
      .putLong(453973187376924038) // id and version of this class implementation, see ColorPatcherIdGenerator
      .build()

    selectionColorPatcher = object : SvgElementColorPatcherProvider {
      private val svgPatcher = ConcurrentHashMap<UiThemePaletteScope, SvgAttributePatcher>()
      private val paletteSvgPatcher = newSvgPatcher(newPalette = colors) { if (alphaColors.contains(it)) 255 else null }

      override fun digest() = digest

      override fun attributeForPath(path: String): SvgAttributePatcher {
        val scope = paletteScopeManager.getScopeByPath(path) ?: return paletteSvgPatcher
        return svgPatcher.computeIfAbsent(scope) {
          // `255` here is not a mistake - we want to set that corresponding color as non-transparent explicitly
          newSvgPatcher(newPalette = colors) { if (alphaColors.contains(it)) 255 else null }
        }
      }
    }
  }

  return UITheme(id = themeId,
                 bean = theme,
                 colorPatcher = colorPatcher,
                 _providerClassLoader = classLoader,
                 patcher = patcher,
                 selectionColorPatcher = selectionColorPatcher)
}

internal fun toColorString(key: String, darkTheme: Boolean): String {
  val newUiPalette = if (darkTheme) colorPaletteNewDark else colorPaletteNewLight
  newUiPalette.get(key)?.let { return it.lowercase() }

  if (darkTheme) {
    colorPaletteClassic.get("$key.Dark")?.let {
      return it.lowercase()
    }
  }
  return (colorPaletteClassic.get(key) ?: key).lowercase()
}

private val colorPaletteClassic: @NonNls Map<String, String> = java.util.Map.ofEntries(
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

private val colorPaletteNewLight: @NonNls Map<String, String> = java.util.Map.ofEntries(
  java.util.Map.entry("Gray.Fill", "#EBECF0"), // Grey12
  java.util.Map.entry("Gray.SecondaryStroke", "#A8ADBD"), // Grey8
  java.util.Map.entry("Gray.Stroke", "#6C707E"), // Grey6

  java.util.Map.entry("Blue.Fill", "#EDF3FF"), // Blue12
  java.util.Map.entry("Blue.Solid", "#4682FA"), // Blue5
  java.util.Map.entry("Blue.Stroke", "#3574F0"), // Blue4

  java.util.Map.entry("Green.Fill", "#F2FCF3"), // Green11
  java.util.Map.entry("Green.Solid", "#55A76A"), // Green6
  java.util.Map.entry("Green.Stroke", "#208A3C"), // Green4

  java.util.Map.entry("Red.Fill", "#FFF7F7"), // Red12
  java.util.Map.entry("Red.Solid", "#E55765"), // Red5
  java.util.Map.entry("Red.Stroke", "#DB3B4B"), // Red4

  java.util.Map.entry("Yellow.Fill", "#FFFAEB"), // Yellow10
  java.util.Map.entry("Yellow.Solid", "#FFAF0F"), // Yellow4
  java.util.Map.entry("Yellow.Stroke", "#C27D04"), // Yellow2

  java.util.Map.entry("Orange.Fill", "#FFF4EB"), // Orange9
  java.util.Map.entry("Orange.Stroke", "#E56D17"), // Orange4

  java.util.Map.entry("Purple.Fill", "#FAF5FF"), // Purple10
  java.util.Map.entry("Purple.Stroke", "#834DF0"), // Purple4

  java.util.Map.entry("Teal.Fill", "#F2FCFC"), // Teal9
  java.util.Map.entry("Teal.Stroke", "#039BA1") // Teal4
)

private val colorPaletteNewDark: @NonNls Map<String, String> = java.util.Map.ofEntries(
  java.util.Map.entry("Gray.Fill", "#43454A"), // Grey4
  java.util.Map.entry("Gray.SecondaryStroke", "#6F737A"), // Grey7
  java.util.Map.entry("Gray.Stroke", "#CED0D6"), // Grey11

  java.util.Map.entry("Blue.Fill", "#25324D"), // Blue1
  java.util.Map.entry("Blue.Stroke", "#548AF7"), // Blue8

  java.util.Map.entry("Green.Fill", "#253627"), // Green1
  java.util.Map.entry("Green.Stroke", "#57965C"), // Green6

  java.util.Map.entry("Red.Fill", "#402929"), // Red1
  java.util.Map.entry("Red.Stroke", "#DB5C5C"), // Red7

  java.util.Map.entry("Yellow.Fill", "#3D3223"), // Yellow1
  java.util.Map.entry("Yellow.Solid", "#F2C55C"), // Yellow7
  java.util.Map.entry("Yellow.Stroke", "#D6AE58"), // Yellow6

  java.util.Map.entry("Orange.Fill", "#45322B"), // Orange1
  java.util.Map.entry("Orange.Stroke", "#C77D55"), // Orange5

  java.util.Map.entry("Purple.Fill", "#2F2936"), // Purple1
  java.util.Map.entry("Purple.Stroke", "#A571E6"), // Purple8

  java.util.Map.entry("Teal.Fill", "#1D3838"), // Teal1
  java.util.Map.entry("Teal.Stroke", "#24A394") // Teal6
)

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

internal class IJColorUIResource(color: Color, private val name: String) : JBColor(Supplier { color }), UIResource {
  override fun getName(): String = name

  override fun toString(): String = "IJColorUIResource(color=${super.toString()}, name=$name)"
}

private fun createWarnFunction(classLoader: ClassLoader): (String, Throwable?) -> Unit {
  return { message, error ->
    val id = PluginUtil.getPluginId(classLoader)
    logger<UITheme>().warn(PluginException(message, error, id))
  }
}