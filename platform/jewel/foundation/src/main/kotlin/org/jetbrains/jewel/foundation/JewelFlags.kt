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

    /**
     * Enables strict mode, modeled after Android's `StrictMode`: violations of documented Jewel contracts throw an
     * [IllegalStateException] instead of logging an error. The default value is `false`.
     *
     * Currently enforced contracts: shortcut dispatch entry points and their handlers are UI-thread-synchronous.
     *
     * Keep this enabled in development and in tests, and disabled in production, so contract violations surface loudly
     * where they can be fixed rather than crashing users.
     *
     * To set this flag, you can also set the system property `jewel.strictMode` to `true`/`false`, or pass the
     * `-Djewel.strictMode=[true|false]` argument when running your application.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public var strictMode: Boolean = System.getProperty("jewel.strictMode", "false").toBoolean()
}
