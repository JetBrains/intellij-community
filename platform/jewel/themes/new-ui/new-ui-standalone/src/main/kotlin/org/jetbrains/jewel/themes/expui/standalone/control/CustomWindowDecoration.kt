package org.jetbrains.jewel.themes.expui.standalone.control

import java.awt.Shape
import java.awt.Window

interface CustomWindowDecorationSupport {

    fun setCustomDecorationEnabled(window: Window, enabled: Boolean)
    fun setCustomDecorationTitleBarHeight(window: Window, height: Int)
    fun setCustomDecorationHitTestSpotsMethod(window: Window, spots: Map<Shape, Int>)

    /**
     * Default idle implementation for CustomWindowDecorationSupport, it do nothing.
     */
    companion object : CustomWindowDecorationSupport {

        override fun setCustomDecorationEnabled(window: Window, enabled: Boolean) {
        }

        override fun setCustomDecorationTitleBarHeight(window: Window, height: Int) {
        }

        override fun setCustomDecorationHitTestSpotsMethod(window: Window, spots: Map<Shape, Int>) {
        }
    }
}
