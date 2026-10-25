// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.Constraints
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.impl.rendering.debug.ComposePaintInfo
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.scale.IconScale
import java.awt.Component
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.LocalComponent

internal fun Modifier.iconRender(
    renderer: IconRenderer,
    density: Float,
    scale: IconScale? = null,
    updateFlow: MutableIconUpdateFlow,
): Modifier = this then IconRenderNodeElement(renderer, density, scale, updateFlow)

private data class IconRenderNodeElement(
    val renderer: IconRenderer,
    val density: Float,
    val scale: IconScale?,
    val updateFlow: MutableIconUpdateFlow,
) : ModifierNodeElement<IconRenderNode>() {
    override fun create() = IconRenderNode(renderer, density, scale, updateFlow)

    override fun update(node: IconRenderNode) {
        node.renderer = renderer
        node.density = density
        node.scale = scale
        node.updateFlow = updateFlow
    }
}

private class IconRenderNode(
    var renderer: IconRenderer,
    var density: Float,
    var scale: IconScale?,
    var updateFlow: MutableIconUpdateFlow,
) : Modifier.Node(), DrawModifierNode, LayoutModifierNode, CompositionLocalConsumerModifierNode {

    private var job: Job? = null

    override fun onAttach() {
        job = coroutineScope.launch { updateFlow.collect { invalidateDraw() } }
        IconRenderDebugSupport.attached(this)
    }

    override fun onDetach() {
        job?.cancel()
        job = null
        IconRenderDebugSupport.detached(this)
    }

    private var layerPaint: Paint? = null

    private fun obtainPaint(): Paint {
        var target = layerPaint
        if (target == null) {
            target = Paint()
            layerPaint = target
        }
        return target
    }

    override fun ContentDrawScope.draw() {
        val resolvedScaling = renderer.resolve(density, scale)
        val paintingContext = ComposeLayerPaintingContext(this, scaling = resolvedScaling.context)
        val layerRect = Rect(Offset.Zero, Size(size.width, size.height))
        drawIntoCanvas { canvas ->
            canvas.withSaveLayer(layerRect, obtainPaint()) {
                if (IconRenderDebugger.isActive) {
                    traceDraw(resolvedScaling, size.width, size.height) { renderer.render(paintingContext) }
                } else {
                    renderer.render(paintingContext)
                }
            }
        }
    }

    /** The AWT component hosting this composition (Jewel bridge / standalone windows provide it), or `null`. */
    fun hostComponent(): JComponent? =
        try {
            currentValueOf(LocalComponent)
        } catch (_: IllegalStateException) {
            null // not provided (e.g. tests, plain ComposePanel)
        }

    /** Debug-only path (the icon render debugger is active): lets an open icon render capture record this draw. */
    private fun traceDraw(scaling: ResolvedScalingContext, width: Float, height: Float, render: () -> Unit) {
        val host = hostComponent()
        IconRenderDebugger.traceComposePaint(
            renderer,
            renderer.iconDescriptor,
            host,
            info = {
                val position = requireLayoutCoordinates().positionInWindow()
                ComposePaintInfo(
                    hostClass = host?.javaClass?.name,
                    x = position.x,
                    y = position.y,
                    width = width,
                    height = height,
                    density = density,
                    scale = scale?.toString(),
                    finalWidth = scaling.finalDimensions.width,
                    finalHeight = scaling.finalDimensions.height,
                )
            },
            paint = render,
        )
    }

    /** Invalidates the drawing if [paintTarget] is `null` or is (an ancestor or descendant of) the host component. */
    fun requestRedraw(paintTarget: Any?) {
        if (!isAttached) return
        if (paintTarget == null) {
            invalidateDraw()
            return
        }
        val host = hostComponent() ?: return
        val related =
            paintTarget === host ||
                (paintTarget is Component &&
                    (SwingUtilities.isDescendingFrom(paintTarget, host) ||
                        SwingUtilities.isDescendingFrom(host, paintTarget)))
        if (related) invalidateDraw()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val expected = renderer.resolve(density, scale).finalDimensions
        val wrappedConstraints =
            Constraints(
                minWidth = expected.width,
                minHeight = expected.height,
                maxWidth = expected.width,
                maxHeight = expected.height,
            )
        val placeable = measurable.measure(wrappedConstraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

/**
 * Connects [IconRenderNode]s to the icon render debugger (the internal Icon Inspector): Compose caches drawings, so a
 * repaint of the hosting component does not re-run [IconRenderNode.draw]; on [IconRenderDebugger.requestRedraw] the
 * attached nodes invalidate their drawing so an open render capture sees them. Outside of debugging the only cost is
 * the weak registration on attach/detach.
 */
private object IconRenderDebugSupport {
    private val nodes: MutableSet<IconRenderNode> = Collections.newSetFromMap(WeakHashMap())

    init {
        IconRenderDebugger.addRedrawListener { paintTarget ->
            val snapshot = synchronized(nodes) { nodes.toList() }
            for (node in snapshot) {
                node.requestRedraw(paintTarget)
            }
        }
    }

    fun attached(node: IconRenderNode) {
        synchronized(nodes) { nodes.add(node) }
    }

    fun detached(node: IconRenderNode) {
        synchronized(nodes) { nodes.remove(node) }
    }
}
