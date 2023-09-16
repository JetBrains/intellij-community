// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "SSBasedInspection")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.UIThemeBean.Companion.readTheme
import com.intellij.ide.ui.laf.IJColor
import com.intellij.ide.ui.laf.IJColorUIResource
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoImpl
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.ColorUtil
import com.intellij.ui.IdeUICustomization
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
import javax.swing.plaf.ColorUIResource
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

  val additionalEditorSchemes: List<String>
    get() = bean.additionalEditorSchemes ?: emptyList()
  val background: Map<String, Any?>
    get() = bean.background ?: emptyMap()
  val emptyFrameBackground: Map<String, Any?>
    get() = bean.emptyFrameBackground ?: emptyMap()

  fun applyTheme(defaults: UIDefaults) {
    for ((key, value) in bean.colorMap.map) {
      defaults.put("ColorPalette.$key", if (value is UIResource) value else ColorUIResource(value))
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
      val value = color ?: parseUiThemeValue(key = key, value = value, classLoader = providerClassLoader)
      if (key.startsWith("*.")) {
        val tail = key.substring(1)
        addPattern(key, value, defaults)
        for (k in defaults.keys.toTypedArray()) {
          if (k is String && k.endsWith(tail)) {
            if (value is Color && !(value is JBColor && value.name != null)) {
              defaults.put(k, if (value is UIResource) IJColorUIResource(value, key) else IJColor(value, key))
            }
            else {
              defaults.put(k, value)
            }
          }
        }
      }
      else {
        defaults.put(key, value)
      }
    }
  }

  @get:ApiStatus.Internal
  val providerClassLoader: ClassLoader
    get() = _providerClassLoader ?: throw RuntimeException("The theme classloader has already been detached")

  @ApiStatus.Internal
  fun setProviderClassLoader(value: ClassLoader?) {
    _providerClassLoader = value
  }

  val editorSchemePath: String?
    get() = IdeUICustomization.getInstance().getUiThemeEditorSchemePath(id, bean.editorScheme)

  var editorSchemeName: String? = null

  companion object {
    const val FILE_EXT_ENDING: String = ".theme.json"

    @ApiStatus.Internal
    fun loadFromJson(stream: InputStream, themeId: @NonNls String): UITheme {
      val theme = readTheme(JsonFactory().createParser(stream))
      return createTheme(themeId = themeId, theme = theme, parentTheme = resolveParentTheme(theme, themeId))
    }

    fun loadFromJson(data: ByteArray?,
                     themeId: @NonNls String,
                     provider: ClassLoader? = null,
                     iconMapper: ((String) -> String?)? = null): UITheme {
      val theme = readTheme(JsonFactory().createParser(data))
      val parentTheme = resolveParentTheme(theme, themeId)
      return createTheme(theme = theme, parentTheme = parentTheme, provider = provider, iconMapper = iconMapper, themeId = themeId)
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
                              provider: ClassLoader?,
                              iconMapper: ((String) -> String?)? = null,
                              defaultDarkParent: Supplier<UITheme?>?,
                              defaultLightParent: Supplier<UITheme?>?): UITheme {
      val bean = readTheme(JsonFactory().createParser(data))
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
      return createTheme(theme = bean, parentTheme = parent, provider = provider, iconMapper = iconMapper, themeId = themeId)
    }

    @TestOnly
    @JvmStatic
    fun getColorPalette(): Map<String, String?> = colorPalette
  }
}

private fun createTheme(theme: UIThemeBean,
                        parentTheme: UIThemeBean?,
                        provider: ClassLoader? = null,
                        iconMapper: ((String) -> String?)? = null,
                        themeId: @NonNls String): UITheme {
  if (parentTheme != null) {
    importFromParentTheme(theme, parentTheme)
  }
  initializeNamedColors(theme)

  val paletteScopeManager = UiThemePaletteScopeManager()
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

    selectionColorPatcher = object : SvgElementColorPatcherProvider {
      private val svgPatcher = ConcurrentHashMap<UiThemePaletteScope, SvgAttributePatcher>()
      private val paletteSvgPatcher: SvgAttributePatcher

      init {
        val hashBuilder = InsecureHashBuilder().stringMap(colors).update(0/* without scope */)
        paletteSvgPatcher = newSvgPatcher(digest = hashBuilder.build(), newPalette = colors) { if (alphaColors.contains(it)) 255 else null }
      }

      override fun attributeForPath(path: String): SvgAttributePatcher {
        val scope = paletteScopeManager.getScopeByPath(path) ?: return paletteSvgPatcher
        return svgPatcher.computeIfAbsent(scope) {
          val hashBuilder = scope.updateHash(InsecureHashBuilder().stringMap(colors).update(1 /* with scope */))
          // `255` here is not a mistake - we want to set that corresponding color as non-transparent explicitly
          newSvgPatcher(digest = hashBuilder.build(), newPalette = colors) { if (alphaColors.contains(it)) 255 else null }
        }
      }
    }
  }

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

      override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?): ClassLoader? = provider
    }

    colorPatcher = configureIcons(theme = theme, paletteScopeManager = paletteScopeManager, iconMap = iconMap)
  }

  return UITheme(id = themeId,
                 bean = theme,
                 colorPatcher = colorPatcher,
                 _providerClassLoader = provider,
                 patcher = patcher,
                 selectionColorPatcher = selectionColorPatcher)
}

private fun configureIcons(theme: UIThemeBean,
                           paletteScopeManager: UiThemePaletteScopeManager,
                           iconMap: Map<String, Any?>): SvgElementColorPatcherProvider? {
  val palette = iconMap.get("ColorPalette") as? Map<*, *> ?: return null
  for (o in palette.keys) {
    val colorKey = o.toString()
    val scope = paletteScopeManager.getScope(colorKey) ?: continue
    val key = toColorString(key = colorKey, darkTheme = theme.dark)
    var v = palette.get(colorKey)
    if (v is String) {
      // named
      v = theme.colorMap.map.get(v) ?: parseColorOrNull(key, null)
    }

    val colorFromKey = parseColorOrNull(key, null)
    if (colorFromKey != null && v is Color) {
      val fillTransparency = v.alpha
      val colorHex = "#" + ColorUtil.toHex(v, false)
      scope.newPalette.put(key, colorHex)
      if (fillTransparency != 255) {
        scope.alphas.put(colorHex, fillTransparency)
      }
    }
  }

  return object : SvgElementColorPatcherProvider {
    override fun attributeForPath(path: String): SvgAttributePatcher? {
      val scope = paletteScopeManager.getScopeByPath(path) ?: return null
      if (scope.newPalette.isEmpty()) {
        return null
      }

      return newSvgPatcher(digest = scope.digest(), newPalette = scope.newPalette) { scope.alphas.get(it) }
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
