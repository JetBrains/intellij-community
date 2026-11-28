// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.ScalingContext

/**
 * @param iconScale the factor of the whole icon, for example `scaled(factor(2f))` of a Swing icon. [contextScale]
 *   contains it. A fit or a fill with `relative = false` ignores the rest of [contextScale], such as the IDE zoom and the
 *   scale of a parent layer, but keeps [iconScale]. So the icon measures and paints at the same size.
 * @param zoom the scale that the renderer applies to every unit, for example the IDE zoom of a Swing renderer.
 *   [contextScale] contains it. A size in logical pixels that already contains the zoom takes it out again, see
 *   `CompoundSize.withoutZoom`.
 */
class DefaultScalingContext(
    override val displayDensity: Float,
    override val contextScale: Float,
    val iconScale: Float,
    val zoom: Float,
) : ScalingContext {
    constructor(displayDensity: Float, contextScale: Float, iconScale: Float) : this(displayDensity, contextScale, iconScale, zoom = 1f)

    constructor(displayDensity: Float, contextScale: Float) : this(displayDensity, contextScale, iconScale = 1f, zoom = 1f)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultScalingContext

        if (displayDensity != other.displayDensity) return false
        if (contextScale != other.contextScale) return false
        if (iconScale != other.iconScale) return false
        if (zoom != other.zoom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = displayDensity.hashCode()
        result = 31 * result + contextScale.hashCode()
        result = 31 * result + iconScale.hashCode()
        result = 31 * result + zoom.hashCode()
        return result
    }

    override fun toString(): String =
        "DefaultScalingContext(displayDensity=$displayDensity, contextScale=$contextScale, iconScale=$iconScale, zoom=$zoom)"
}

/** The scale of the whole icon in this context, see [DefaultScalingContext.iconScale]. */
val ScalingContext.iconScale: Float
    get() = (this as? DefaultScalingContext)?.iconScale ?: 1f

/** The zoom of the renderer in this context, see [DefaultScalingContext.zoom]. */
val ScalingContext.zoom: Float
    get() = (this as? DefaultScalingContext)?.zoom ?: 1f

/**
 * The context of a nested layer that a parent layer scales by [scale]. The scale of the whole icon and the zoom stay the
 * same.
 */
fun ScalingContext.nested(scale: Float): ScalingContext =
    DefaultScalingContext(displayDensity, contextScale * scale, iconScale, zoom)
