// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader.getDarkIcon
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.icons.CompositeIcon
import com.intellij.ui.icons.DarkIconProvider
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.IconWithToolTip
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.scale.UserScaleContext
import com.intellij.util.ArrayUtilRt
import com.intellij.util.IconUtil
import com.intellij.util.IconUtil.copy
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.JBCachingScalableIcon
import org.intellij.lang.annotations.MagicConstant
import java.awt.Component
import java.awt.Graphics
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val LOG = logger<LayeredIcon>()

private class IconState(@JvmField val icons: Array<Icon?>,
                        @JvmField val disabledLayers: BooleanArray)

open class LayeredIcon : JBCachingScalableIcon<LayeredIcon>, DarkIconProvider, CompositeIcon, IconWithToolTip {
  private val iconListSupplier: Supplier<IconState>
  private var scaledIcons: Array<Icon?>?
  private var hShifts: IntArray
  private var vShifts: IntArray
  private var xShift = 0
  private var yShift = 0
  private var width = 0
  private var height = 0

  private var sizeIsDirty = true

  init {
    scaleContext.addUpdateListener(UserScaleContext.UpdateListener { updateSize(allLayers) })
    @Suppress("LeakingThis")
    setAutoUpdateScaleContext(false)
  }

  constructor(layerCount: Int) {
    val state = IconState(icons = arrayOfNulls(layerCount), disabledLayers = BooleanArray(layerCount))
    iconListSupplier = Supplier { state }
    hShifts = IntArray(layerCount)
    vShifts = IntArray(layerCount)
    scaledIcons = null
  }

  constructor() : this(layerCount = 0)

  @Deprecated(message = "Use [layeredIcon]")
  constructor(vararg icons: Icon) {
    val layerCount = icons.size

    val state = IconState(icons = arrayOfNulls(layerCount), disabledLayers = BooleanArray(layerCount))
    icons.copyInto(state.icons)
    iconListSupplier = Supplier { state }

    hShifts = IntArray(layerCount)
    vShifts = IntArray(layerCount)
    scaledIcons = null
  }

  private constructor(icons: Supplier<Array<out Icon>>) {
    iconListSupplier = SynchronizedClearableLazy {
      @Suppress("UNCHECKED_CAST")
      val result = icons.get() as Array<Icon?>
      hShifts = IntArray(result.size)
      vShifts = IntArray(result.size)
      sizeIsDirty = true

      IconState(icons = result, disabledLayers = BooleanArray(result.size))
    }

    hShifts = ArrayUtilRt.EMPTY_INT_ARRAY
    vShifts = ArrayUtilRt.EMPTY_INT_ARRAY
    scaledIcons = null
  }

  companion object {
    @JvmField
    val ADD_WITH_DROPDOWN: Icon = LayeredIcon(Supplier { arrayOf(AllIcons.General.Add, AllIcons.General.Dropdown) })
    @JvmField
    val EDIT_WITH_DROPDOWN: Icon = LayeredIcon(Supplier { arrayOf(AllIcons.Actions.Edit, AllIcons.General.Dropdown) })
    @JvmField
    val GEAR_WITH_DROPDOWN: Icon = LayeredIcon(Supplier { arrayOf(AllIcons.General.GearPlain, AllIcons.General.Dropdown) })

    @JvmStatic
    fun layeredIcon(icons: Supplier<Array<out Icon>>): LayeredIcon = LayeredIcon(icons)

    @Suppress("DEPRECATION")
    @JvmStatic
    fun layeredIcon(icons: Array<out Icon>): LayeredIcon = LayeredIcon(*icons)

    @JvmStatic
    fun create(backgroundIcon: Icon?, foregroundIcon: Icon?): Icon {
      val layeredIcon = LayeredIcon(2)
      layeredIcon.setIcon(backgroundIcon, 0)
      layeredIcon.setIcon(foregroundIcon, 1)
      return layeredIcon
    }
  }

  private constructor(icon: LayeredIcon, replacer: IconReplacer?) : super(icon) {
    val otherState = icon.iconListSupplier.get()
    val icons = otherState.icons.copyOf()
    if (replacer != null) {
      for ((i, subIcon) in icons.withIndex()) {
        icons[i] = replacer.replaceIcon(subIcon)
      }
    }

    val state = IconState(icons = icons, disabledLayers = otherState.disabledLayers)
    iconListSupplier = Supplier { state }

    scaledIcons = null
    hShifts = icon.hShifts.copyOf()
    vShifts = icon.vShifts.copyOf()
    xShift = icon.xShift
    yShift = icon.yShift
    width = icon.width
    height = icon.height
  }

  val allLayers: Array<Icon?>
    get() = iconListSupplier.get().icons

  override fun replaceBy(replacer: IconReplacer) = LayeredIcon(icon = this, replacer = replacer)

  override fun copy(): LayeredIcon = LayeredIcon(icon = this, replacer = null)

  override fun deepCopy(): LayeredIcon {
    return LayeredIcon(icon = this, replacer = object : IconReplacer {
      override fun replaceIcon(icon: Icon?): Icon? {
        return icon?.let { copy(icon = it, ancestor = null) }
      }
    })
  }

  private fun getOrComputeScaledIcons(): Array<Icon?> {
    scaledIcons?.let {
      return it
    }
    return scaleIcons(icons = allLayers, scale = scale).also { scaledIcons = it }
  }

  override fun withIconPreScaled(preScaled: Boolean): LayeredIcon {
    super.withIconPreScaled(preScaled)
    updateSize(allLayers)
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LayeredIcon) return false
    if (height != other.height) return false
    if (width != other.width) return false
    if (xShift != other.xShift) return false
    if (yShift != other.yShift) return false
    if (!hShifts.contentEquals(other.hShifts)) return false
    if (!allLayers.contentEquals(other.allLayers)) return false
    if (!vShifts.contentEquals(other.vShifts)) return false
    return true
  }

  // for cache, we need distinct hash code, but we cannot compute it by content, as we should not call iconListSupplier to compute hash code
  override fun hashCode() = System.identityHashCode(this)

  fun setIcon(icon: Icon?, layer: Int) {
    setIcon(icon = icon, layer = layer, hShift = 0, vShift = 0)
  }

  override fun getIcon(layer: Int) = allLayers[layer]

  override fun getIconCount() = allLayers.size

  fun setIcon(icon: Icon?, layer: Int, hShift: Int, vShift: Int) {
    if (icon is LayeredIcon) {
      icon.checkIHaventIconInsideMe(this)
    }

    val allLayers = allLayers
    allLayers[layer] = icon
    scaledIcons = null
    hShifts[layer] = hShift
    vShifts[layer] = vShift
    sizeIsDirty = true
  }

  /**
   *
   * @param constraint is expected to be one of the compass-directions or CENTER
   */
  fun setIcon(icon: Icon, layer: Int, @MagicConstant(valuesFromClass = SwingConstants::class) constraint: Int) {
    val width = getIconWidth()
    val height = getIconHeight()
    if (width <= 1 || height <= 1) {
      setIcon(icon, layer)
      return
    }

    val w = icon.iconWidth
    val h = icon.iconHeight

    val x: Int
    val y: Int
    when (constraint) {
      SwingConstants.CENTER -> {
        x = (width - w) / 2
        y = (height - h) / 2
      }
      SwingConstants.NORTH -> {
        x = (width - w) / 2
        y = 0
      }
      SwingConstants.NORTH_EAST -> {
        x = width - w
        y = 0
      }
      SwingConstants.EAST -> {
        x = width - w
        y = (height - h) / 2
      }
      SwingConstants.SOUTH_EAST -> {
        x = width - w
        y = height - h
      }
      SwingConstants.SOUTH -> {
        x = (width - w) / 2
        y = height - h
      }
      SwingConstants.SOUTH_WEST -> {
        x = 0
        y = height - h
      }
      SwingConstants.WEST -> {
        x = 0
        y = (height - h) / 2
      }
      SwingConstants.NORTH_WEST -> {
        x = 0
        y = 0
      }
      else -> throw IllegalArgumentException(
        "The constraint should be one of SwingConstants' compass-directions [1..8] or CENTER [0], actual value is $constraint")
    }
    setIcon(icon, layer, x, y)
  }

  @Suppress("SpellCheckingInspection")
  private fun checkIHaventIconInsideMe(icon: Icon) {
    LOG.assertTrue(icon !== this)
    for (child in allLayers) {
      if (child is LayeredIcon) {
        child.checkIHaventIconInsideMe(icon)
      }
    }
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize(allLayers)
    }

    val icons = getOrComputeScaledIcons()
    val disabledLayers = iconListSupplier.get().disabledLayers
    for ((i, icon) in icons.withIndex()) {
      if (icon == null || disabledLayers[i]) {
        continue
      }

      val xOffset = floor(x + scaleVal((xShift + getHShift(i)).toDouble(), ScaleType.OBJ_SCALE)).toInt()
      val yOffset = floor(y + scaleVal((yShift + getVShift(i)).toDouble(), ScaleType.OBJ_SCALE)).toInt()
      icon.paintIcon(c, g, xOffset, yOffset)
    }
  }

  fun isLayerEnabled(layer: Int): Boolean = !iconListSupplier.get().disabledLayers[layer]

  fun setLayerEnabled(layer: Int, enabled: Boolean) {
    val disabledLayers = iconListSupplier.get().disabledLayers
    if (disabledLayers[layer] == enabled) {
      disabledLayers[layer] = !enabled
      clearCachedScaledValue()
    }
  }

  override fun getIconWidth(): Int {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize(allLayers)
    }
    return ceil(scaleVal(width.toDouble(), ScaleType.OBJ_SCALE)).toInt()
  }

  override fun getIconHeight(): Int {
    scaleContext.update()
    if (sizeIsDirty) {
      updateSize(allLayers)
    }
    return ceil(scaleVal(height.toDouble(), ScaleType.OBJ_SCALE)).toInt()
  }

  fun getHShift(i: Int): Int {
    return floor(scaleVal(hShifts[i].toDouble(), ScaleType.USR_SCALE)).toInt()
  }

  fun getVShift(i: Int): Int {
    return floor(scaleVal(vShifts[i].toDouble(), ScaleType.USR_SCALE)).toInt()
  }

  protected fun updateSize(allLayers: Array<Icon?>) {
    sizeIsDirty = false

    var minX = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var minY = Int.MAX_VALUE
    var maxY = Int.MIN_VALUE
    var allIconsAreNull = true
    for ((i, icon) in allLayers.withIndex()) {
      if (icon == null) {
        continue
      }

      allIconsAreNull = false
      val hShift = getHShift(i)
      val vShift = getVShift(i)
      minX = min(minX, hShift)
      maxX = max(maxX, (hShift + icon.iconWidth))
      minY = min(minY, vShift)
      maxY = max(maxY, (vShift + icon.iconHeight))
    }

    if (allIconsAreNull) {
      return
    }

    width = maxX - minX
    height = maxY - minY
    if (allLayers.size > 1) {
      xShift = -minX
      yShift = -minY
    }
  }

  override fun getDarkIcon(isDark: Boolean): Icon {
    val newIcon = copy()
    for ((i, icon) in newIcon.allLayers.withIndex()) {
      newIcon.allLayers[i] = icon?.let { getDarkIcon(icon = it, dark = isDark) }
    }
    return newIcon
  }

  override fun toString() = "LayeredIcon(w=$width, h=$height, icons=[${allLayers.joinToString(", ")}]"

  override fun getToolTip(composite: Boolean) = combineIconTooltips(allLayers)
}

internal fun combineIconTooltips(icons: Array<Icon?>): @NlsContexts.Tooltip String? {
  // If a layered icon contains only a single non-null layer and other layers are null, its tooltip is not composite.
  var singleIcon: Icon? = null
  for (icon in icons) {
    if (icon != null) {
      if (singleIcon != null) {
        val result: @NlsContexts.Tooltip StringBuilder = StringBuilder()
        val seenTooltips = HashSet<String>()
        buildCompositeTooltip(icons = icons, result = result, seenTooltips = seenTooltips)
        @Suppress("HardCodedStringLiteral")
        return result.toString()
      }
      singleIcon = icon
    }
  }
  if (singleIcon != null) {
    return if (singleIcon is IconWithToolTip) singleIcon.getToolTip(false) else null
  }
  return null
}

private fun buildCompositeTooltip(icons: Array<Icon?>, result: StringBuilder, seenTooltips: MutableSet<String>) {
  for (i in icons.indices) {
    // the first layer is the actual object (noun), other layers are modifiers (adjectives), so put a first object in the last position
    val icon = if (i == icons.size - 1) icons[0] else icons[i + 1]
    if (icon is LayeredIcon) {
      buildCompositeTooltip(icons = icon.allLayers, result = result, seenTooltips = seenTooltips)
    }
    else if (icon is IconWithToolTip) {
      val toolTip = icon.getToolTip(true)
      if (toolTip != null && seenTooltips.add(toolTip)) {
        if (!result.isEmpty()) {
          result.append(' ')
        }
        result.append(toolTip)
      }
    }
  }
}

internal fun scaleIcons(icons: Array<Icon?>, scale: Float): Array<Icon?> {
  if (scale == 1f) {
    return icons
  }

  return Array(icons.size) { index ->
    icons[index]?.let { IconUtil.scale(icon = it, ancestor = null, scale = scale) }
  }
}