package org.jetbrains.jewel.foundation

import org.jetbrains.annotations.ApiStatus

/**
 * JewelFlags is an object that holds configuration flags used in the Jewel library.
 *
 * These flags can control specific behaviors or enable experimental features within Jewel.
 */
public object JewelFlags {
    /**
     * Enable custom popups handling in Jewel. The default value is `false`.
     *
     * If enabled, the Jewel library will use a custom popup renderer, using separate windows instead of being drawn
     * onto the same layer.
     *
     * This is an experimental feature and may not be fully stable. When enabled, Compose's popup settings are ignored
     * when using Jewel popups and tooltips. This means that setting `compose.layers.type` will have no effect on Jewel
     * popups and tooltips.
     *
     * To set this flag, you can also set the system property `jewel.customPopupRender` to `true`/`false`, or pass the
     * `-Djewel.customPopupRender=[true|false]` argument when running your application.
     *
     * Note that this flag affects popups, menus and tooltips rendering from Jewel Components. It does not affect
     * `Dialog`s.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public var useCustomPopupRenderer: Boolean = System.getProperty("jewel.customPopupRender", "false").toBoolean()
}
