package org.jetbrains.jewel.window.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import com.jetbrains.WindowDecorations
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.LocalTitleBarInfo
import org.jetbrains.jewel.window.TitleBarInfo
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.collections.set

internal data class ClientRegion(
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
)

public fun Modifier.clientRegion(key: String): Modifier = then(RegisterClientRegionElement(key))

private data class RegisterClientRegionElement(
    private val key: String
) : ModifierNodeElement<RegisterClientRegionNode>() {

    override fun create() = RegisterClientRegionNode(key)

    override fun update(node: RegisterClientRegionNode) = node.updateKey(key)

    override fun InspectorInfo.inspectableProperties() {
        name = "registerRegion"
        properties["key"] = key
    }
}

private class RegisterClientRegionNode(
    var key: String
) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var titleBarInfo: TitleBarInfo? = null

    override fun onAttach() {
        titleBarInfo = currentValueOf(LocalTitleBarInfo)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val info = titleBarInfo ?: return
        val position = coordinates.positionInWindow()
        val size = coordinates.size

        info.clientRegions[key] = ClientRegion(
            x = position.x,
            y = position.y,
            width = size.width,
            height = size.height
        )
    }

    override fun onDetach() {
        titleBarInfo?.clientRegions?.remove(key)
        titleBarInfo = null
    }

    fun updateKey(newKey: String) {
        if (key != newKey) {
            titleBarInfo?.clientRegions?.remove(key)
            key = newKey
        }
    }
}

@Composable
internal fun DecoratedWindowScope.WindowMouseEventEffect(titleBar: WindowDecorations.CustomTitleBar) {
    val titleBarInfo = LocalTitleBarInfo.current

    DisposableEffect(window, window.graphicsConfiguration, titleBar) {
        val graphicsConfig = window.graphicsConfiguration
        val scaleX = graphicsConfig?.defaultTransform?.scaleX ?: 1.0
        val scaleY = graphicsConfig?.defaultTransform?.scaleY ?: 1.0
        val listener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = updateHitTest(e)
            override fun mouseReleased(e: MouseEvent) = updateHitTest(e)
            override fun mouseDragged(e: MouseEvent) = updateHitTest(e)
            override fun mouseMoved(e: MouseEvent) = updateHitTest(e)

            private fun updateHitTest(e: MouseEvent) {
                // Convert from AWT raw pixels to Compose positionInWindow() coordinates
                val x = e.x * scaleX
                val y = e.y * scaleY

                val isClientRegion = titleBarInfo.clientRegions.any { region ->
                    (x >= region.value.x && x <= region.value.x + region.value.width) &&
                        (y >= region.value.y && y <= region.value.y + region.value.height)
                }

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
