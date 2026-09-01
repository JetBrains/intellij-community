// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.ATTR_FILL
import com.intellij.ui.svg.ATTR_FILL_OPACITY
import com.intellij.ui.svg.ATTR_STROKE
import com.intellij.ui.svg.ATTR_STROKE_OPACITY
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicToggleButtonUI
import kotlin.math.max

private const val ICONS_DIR = "com/intellij/ide/ui/laf/icons/"

/**
 * Attributes naming the theme color a painted element takes its fill or stroke from, the same
 * convention used by the Got It tooltip icons (see `com.intellij.ui.GotItComponentBuilderKt`).
 * The value is a theme color name, which [UITheme][com.intellij.ide.ui.UITheme] registers into UI
 * defaults under [PALETTE_PREFIX].
 */
private const val COLOR_FILL_KEY = "color-fill-key"
private const val COLOR_STROKE_KEY = "color-stroke-key"
private const val PALETTE_PREFIX = "ColorPalette."

/**
 * Every theme color the `toggle*.svg` assets reference. Only used to build the patcher digest — the
 * patcher itself resolves whatever key an element carries — so an asset may use any subset.
 */
private val TOGGLE_COLOR_KEYS: List<String> = listOf(
  "toggle-on-bg", "toggle-on-border", "toggle-on-notch",
  "toggle-off-bg", "toggle-off-border", "toggle-off-notch",
  "toggle-on-disabled-bg", "toggle-on-disabled-border", "toggle-on-disabled-notch",
  "toggle-off-disabled-bg", "toggle-off-disabled-border", "toggle-off-disabled-notch",
  "toggle-focus-border",
)

/** Identity of this patcher implementation, so its digests never collide with another patcher's. */
private const val PATCHER_IMPL_ID = 6222195294178155463L

/**
 * Asset the layout size is taken from. Every `toggle*.svg` shares one canvas, so any of them would
 * do; using a fixed one keeps [IslandsOnOffButtonUI.getPreferredSize] independent of button state.
 */
private const val REFERENCE_ICON = "toggleOff"

/** Minimum component height, so a toggle lines up with other controls in list rows and forms. */
private const val MIN_HEIGHT = 32

/**
 * Recolors a `toggle*.svg` element from the theme color its [COLOR_FILL_KEY] / [COLOR_STROKE_KEY]
 * attribute names. Elements without those attributes keep the color baked into the asset.
 */
private class TogglePalettePatcher(private val colors: Map<String, Color>)
  : SVGLoader.SvgElementColorPatcherProvider, SvgAttributePatcher {

  private val digest: LongArray = longArrayOf(colors.entries.fold(0L) { hash, (key, color) ->
    hash * 31 + key.hashCode() * 31L + color.rgb
  }, PATCHER_IMPL_ID)

  override fun digest(): LongArray = digest

  override fun attributeForPath(path: String): SvgAttributePatcher = this

  override fun patchColors(attributes: MutableMap<String, String>) {
    patch(attributes, COLOR_FILL_KEY, ATTR_FILL, ATTR_FILL_OPACITY)
    patch(attributes, COLOR_STROKE_KEY, ATTR_STROKE, ATTR_STROKE_OPACITY)
  }

  private fun patch(attributes: MutableMap<String, String>, colorKey: String, attribute: String, opacityAttribute: String) {
    if (!attributes.containsKey(attribute)) {
      return
    }
    val color = colors.get(attributes.get(colorKey) ?: return) ?: return

    attributes.put(attribute, "rgb(${color.red},${color.green},${color.blue})")

    // the resolved color carries its own alpha, so an opacity baked into the asset must not survive it
    if (color.alpha == 255) {
      attributes.remove(opacityAttribute)
    }
    else {
      attributes.put(opacityAttribute, (color.alpha / 255f).toString())
    }
  }
}

/**
 * Islands-themed on/off toggle UI delegate.
 *
 * Renders a pill-shaped track with a notch indicator (no text labels) from `toggle*.svg` icons.
 * Track, border, notch and focus ring colors are named by the assets themselves through
 * `color-fill-key` / `color-stroke-key` attributes and resolved against the current theme by
 * [TogglePalettePatcher], so a single asset per state serves every theme and adding a state needs
 * no platform-side color table.
 *
 * State is taken from [javax.swing.ButtonModel] and mapped to an asset by name suffix, the same
 * scheme [com.intellij.util.ui.LafIconLookup] uses for checkboxes and radio buttons. The focus ring
 * is part of the focused assets rather than painted here: all six assets share one canvas that
 * reserves the ring's padding around the track, so gaining focus swaps the image without moving the
 * track or resizing the component.
 *
 * Registered in Islands theme JSON via `"OnOffButtonUI"` key.
 */
@Suppress("unused")
internal class IslandsOnOffButtonUI : BasicToggleButtonUI() {

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): IslandsOnOffButtonUI {
      return IslandsOnOffButtonUI()
    }

    private var cachedColors: Map<String, Color> = emptyMap()
    private var cachedPatcher: TogglePalettePatcher? = null

    /**
     * Rebuilt whenever the resolved colors change, which covers both theme switches and Islands
     * being toggled on or off without needing to listen for them.
     */
    @Synchronized
    private fun patcher(): TogglePalettePatcher {
      val colors = TOGGLE_COLOR_KEYS.mapNotNull { key -> UIManager.getColor(PALETTE_PREFIX + key)?.let { key to it } }.toMap()
      var patcher = cachedPatcher
      if (patcher == null || colors != cachedColors) {
        patcher = TogglePalettePatcher(colors)
        cachedColors = colors
        cachedPatcher = patcher
      }
      return patcher
    }

    private fun findIcon(name: String): Icon? {
      val icon = findIconUsingNewImplementation(path = "$ICONS_DIR$name.svg",
                                                classLoader = IslandsOnOffButtonUI::class.java.classLoader)
      // the patched copy is cheap: rasterization is shared through the patcher digest
      return (icon as? CachedImageIcon)?.createWithPatcher(colorPatcher = patcher()) ?: icon
    }

    /**
     * `Disabled` wins over `Focused` because a disabled toggle is not focusable and there is no
     * combined asset. A validation outline suppresses the focus ring, as in [DarculaCheckBoxUI].
     */
    private fun getIcon(button: OnOffButton): Icon? {
      val state = when {
        !button.isEnabled -> "Disabled"
        button.hasFocus() && DarculaUIUtil.getOutline(button) == null -> "Focused"
        else -> ""
      }
      return findIcon((if (button.isSelected) "toggleOn" else "toggleOff") + state)
    }
  }

  override fun installUI(c: JComponent) {
    super.installUI(c)
    c.alignmentY = 0.5f
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val icon = findIcon(REFERENCE_ICON)
    return Dimension(icon?.iconWidth ?: JBUIScale.scale(32),
                     max(JBUIScale.scale(MIN_HEIGHT), icon?.iconHeight ?: JBUIScale.scale(22)))
  }

  override fun getMinimumSize(c: JComponent): Dimension = getPreferredSize(c)

  override fun getMaximumSize(c: JComponent): Dimension = getPreferredSize(c)

  override fun paint(g: Graphics, c: JComponent) {
    if (c !is OnOffButton) return
    val icon = getIcon(c) ?: return
    icon.paintIcon(c, g, (c.width - icon.iconWidth) / 2, (c.height - icon.iconHeight) / 2)
  }
}
