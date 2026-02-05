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
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Constraints
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.scale.IconScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
) : Modifier.Node(), DrawModifierNode, LayoutModifierNode {

    private var job: Job? = null

    override fun onAttach() {
        job = coroutineScope.launch { updateFlow.collect { invalidateDraw() } }
    }

    override fun onDetach() {
        job?.cancel()
        job = null
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
        drawIntoCanvas { canvas -> canvas.withSaveLayer(layerRect, obtainPaint()) { renderer.render(paintingContext) } }
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
