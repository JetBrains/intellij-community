package org.jetbrains.jewel.window.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize
import com.jetbrains.WindowDecorations
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.LocalTitleBarInfo
import org.jetbrains.jewel.window.TitleBarInfo

/**
 * Registers a composable element as a client region within a [DecoratedWindow]'s title bar.
 *
 * Client regions are interactive areas of the title bar that should respond to mouse events as if they were part of the
 * window's client area (the window's content), rather than the draggable title bar. This is essential for interactive
 * title bar controls like buttons, menus, or other widgets that should not trigger window dragging.
 *
 * **When to use:**
 * - Your custom title bar contains interactive elements (buttons, dropdowns, etc.)
 * - You want these elements to respond to clicks instead of causing the window to be dragged
 * - You're building a custom title bar layout and need to define clickable regions
 *
 * **Example usage:**
 *
 * ```kotlin
 * DecoratedWindow(onCloseRequest = { ... }) {
 *     TitleBar {
 *         Button(
 *             onClick = { /* handle click */ },
 *             modifier = Modifier.clientRegion("menu_button")
 *         ) {
 *             Text("Menu")
 *         }
 *     }
 * }
 * ```
 *
 * @param key A unique identifier for this client region. Used internally to track and update the region. Should be
 *   unique within the same window's title bar.
 * @return A modified [Modifier] that registers this composable as a client region.
 * @see DecoratedWindow
 * @see WindowMouseEventEffect
 */
public fun Modifier.clientRegion(key: String): Modifier = then(RegisterClientRegionElement(key))

private data class RegisterClientRegionElement(private val key: String) :
    ModifierNodeElement<RegisterClientRegionNode>() {
    override fun create() = RegisterClientRegionNode(key)

    override fun update(node: RegisterClientRegionNode) {
        node.updateKey(key)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "registerRegion"
        properties["key"] = key
    }
}

private class RegisterClientRegionNode(var key: String) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var titleBarInfo: TitleBarInfo? = null

    override fun onAttach() {
        titleBarInfo = currentValueOf(LocalTitleBarInfo)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val info = titleBarInfo ?: return
        val rect = Rect(coordinates.positionInWindow(), coordinates.size.toSize())

        info.clientRegions[key] = rect
    }

    override fun onDetach() {
        titleBarInfo?.clientRegions?.remove(key)
        titleBarInfo = null
    }

    fun updateKey(newKey: String) {
        if (key == newKey) return

        val region = titleBarInfo?.clientRegions?.remove(key)

        if (region != null) {
            titleBarInfo?.clientRegions[newKey] = region
        }

        key = newKey
    }
}

/**
 * Sets up mouse event handling for interactive title bar regions in a [DecoratedWindow].
 *
 * This effect monitors mouse movements and clicks on the window, determining whether the cursor is over a client region
 * (interactive title bar element) or the draggable title bar itself. It communicates hit test results to the platform's
 * window decorations system, allowing proper handling of mouse interactions.
 *
 * @param titleBar The platform window decorations object that receives hit test updates.
 * @see clientRegion
 * @see DecoratedWindow
 */
@Composable
internal fun DecoratedWindowScope.WindowMouseEventEffect(titleBar: WindowDecorations.CustomTitleBar) {
    val titleBarInfo = LocalTitleBarInfo.current

    DisposableEffect(window, window.graphicsConfiguration, titleBar) {
        val graphicsConfig = window.graphicsConfiguration
        val scaleX = graphicsConfig?.defaultTransform?.scaleX ?: 1.0
        val scaleY = graphicsConfig?.defaultTransform?.scaleY ?: 1.0
        val listener =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    updateHitTest(e)
                }

                override fun mouseReleased(e: MouseEvent) {
                    updateHitTest(e)
                }

                override fun mouseDragged(e: MouseEvent) {
                    updateHitTest(e)
                }

                override fun mouseMoved(e: MouseEvent) {
                    updateHitTest(e)
                }

                private fun updateHitTest(e: MouseEvent) {
                    val point = Offset(x = (e.x * scaleX).toFloat(), y = (e.y * scaleY).toFloat())

                    val isClientRegion = titleBarInfo.clientRegions.any { it.value.contains(point) }

                    titleBar.forceHitTest(isClientRegion)
                }
            }
        window.addMouseListener(listener)
        window.addMouseMotionListener(listener)

        onDispose {
            window.removeMouseListener(listener)
            window.removeMouseMotionListener(listener)
        }
    }
}
