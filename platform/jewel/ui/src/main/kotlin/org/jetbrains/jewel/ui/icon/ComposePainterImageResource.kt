// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.util.fastRoundToInt
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.SizeVariantCache
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ScalingContext

/**
 * An SVG or other vector image drawn by [painter].
 *
 * @param painter draws the image
 * @param modifiers the image modifiers of the image, or `null` for none
 * @param variantLoader loads the painter of the `@WxH` size variant W×H of the image, or gives `null` if it does not
 *   exist; `null` for an image without size variants
 */
internal class ComposePainterImageResource(
    val painter: Painter,
    val modifiers: ImageModifiers?,
    private val variantLoader: ((width: Int, height: Int) -> Painter?)? = null,
) : ImageResource {
    override val width: Int = painter.intrinsicSize.width.fastRoundToInt()
    override val height: Int = painter.intrinsicSize.height.fastRoundToInt()

    /** The painters of the variants that exist and are drawn for W×H. */
    private val variants = SizeVariantCache<Painter>()

    /**
     * The painter for the device pixels [width] × [height] in [scaling]: the painter of the `@WxH` variant that the
     * [SizeVariantPolicy] of [modifiers] selects, as the Swing renderer selects it, or else [painter].
     */
    fun painterFor(width: Int, height: Int, scaling: ScalingContext): Painter {
        val loader = variantLoader ?: return painter
        val painted = SizeVariants.paintedSize(this.width, this.height, width, height, scaling) ?: return painter
        val policy = (modifiers as? DefaultImageModifiers)?.sizeVariantPolicy ?: SizeVariantPolicy.Exact
        // Compose has no IDE zoom: the context scale holds only the scale of the icon
        val (variantWidth, variantHeight) =
            SizeVariants.chooseVariant(policy, painted, zoom = 1.0, this.width, this.height) { w, h ->
                variant(loader, w, h) != null
            } ?: return painter
        return variant(loader, variantWidth, variantHeight) ?: painter
    }

    private fun variant(loader: (Int, Int) -> Painter?, width: Int, height: Int): Painter? =
        variants.get(width, height) {
            // like the legacy check, accept the variant only if it really is drawn for W×H
            loader(width, height)?.takeIf { candidate ->
                candidate.intrinsicSize.width.fastRoundToInt() == width &&
                    candidate.intrinsicSize.height.fastRoundToInt() == height
            }
        }
}
