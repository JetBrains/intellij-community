package org.jetbrains.jewel.foundation

import org.jetbrains.annotations.ApiStatus

/**
 * Enables the new compositing strategy for rendering directly into Swing Graphics. This fixes z-order problems and
 * artifacts on resizing, but has a performance penalty when using infinitely repeating animations.
 *
 * We assume the majority of our users will want this flag to be on, so this convenience function is provided to that
 * end. Make sure you call it **before** you initialize any Compose content. The function is idempotent and extremely
 * cheap, so you can call it on any entry point.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun enableNewSwingCompositing() {
    System.setProperty("compose.swing.render.on.graphics", "true")
}
