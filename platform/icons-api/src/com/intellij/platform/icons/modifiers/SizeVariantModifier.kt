// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager

/**
 * Whether image layers may be drawn from a hand-drawn size variant of their SVG (`icon@20x20.svg` next to `icon.svg`) instead
 * of scaling the base artwork. The design size is the logical size the image is painted at, with the screen density and the
 * IDE zoom divided out.
 *
 * The Swing and the Compose (Jewel) renderers support size variants for images from a class loader resource, as the legacy
 * icons do. The Swing renderer also supports them for a bridged legacy icon. An image from a file or a URL has no size
 * variants. A variant is used only if its own size is W×H.
 */
enum class SizeVariantPolicy {
    /**
     * The default: use `@WxH` only when the design size is exactly W×H (parity with the legacy icons'
     * `loadIconCustomVersion`).
     */
    Exact,

    /**
     * Use the largest `@WxH` variant with W ≤ design width and H ≤ design height, scaled up to the design size; the base
     * artwork if none fits. A variant is never smaller than the base artwork's own size: when nothing between the base size
     * and the design size exists, the (scaled) base is the better fit. Only the variants with the aspect ratio of the base
     * artwork are looked for, and at most 64 sizes below the design size.
     */
    Fitting,

    /** Never use size variants; always scale the base artwork. */
    None,
}

/**
 * Sets the [SizeVariantPolicy] for the image layers this modifier applies to, including those nested inside (nested icons,
 * layouts, animation frames). The innermost explicit setting wins; without one, [SizeVariantPolicy.Exact] applies.
 */
fun IconModifier.sizeVariants(policy: SizeVariantPolicy): IconModifier = this then IconManager.modifiers().sizeVariants(policy)
