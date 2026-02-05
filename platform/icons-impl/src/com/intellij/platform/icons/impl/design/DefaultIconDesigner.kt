// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAnimationDesigner
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.impl.DefaultLayeredIcon
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.layers.SpacerIconLayer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier

abstract class DefaultIconDesigner : IconDesigner {
    protected val layers: MutableList<IconLayer> = mutableListOf<IconLayer>()

    override fun image(resourceLoader: ImageResourceLocation, modifier: IconModifier) {
        layers.add(ImageIconLayer(resourceLoader, modifier))
    }

    override fun icon(icon: Icon, modifier: IconModifier) {
        layers.add(NestedIconLayer(icon, modifier))
    }

    override fun box(modifier: IconModifier, builder: IconDesigner.() -> Unit) {
        layout(LayoutIconLayer.LayoutDirection.Box, modifier, builder)
    }

    override fun row(modifier: IconModifier, builder: IconDesigner.() -> Unit) {
        layout(LayoutIconLayer.LayoutDirection.Row, modifier, builder)
    }

    override fun column(modifier: IconModifier, builder: IconDesigner.() -> Unit) {
        layout(LayoutIconLayer.LayoutDirection.Column, modifier, builder)
    }

    override fun spacer(modifier: IconModifier) {
        layers.add(SpacerIconLayer(modifier))
    }

    private fun layout(
        direction: LayoutIconLayer.LayoutDirection,
        modifier: IconModifier,
        builder: IconDesigner.() -> Unit,
    ) {
        val nestedIconDesigner = createNestedDesigner()
        nestedIconDesigner.builder()
        layers.add(LayoutIconLayer(nestedIconDesigner.buildLayers(), direction, modifier))
    }

    override fun animation(modifier: IconModifier, builder: IconAnimationDesigner.() -> Unit) {
        val designer = DefaultIconAnimationDesigner(this)
        designer.builder()
        layers.add(AnimatedIconLayer(designer.build(), modifier))
    }

    override fun shape(color: Color, shape: Shape, modifier: IconModifier) {
        layers.add(ShapeIconLayer(color, shape, modifier))
    }

    abstract fun createNestedDesigner(): DefaultIconDesigner

    fun build(): DefaultLayeredIcon = DefaultLayeredIcon(buildLayers())

    fun buildLayers(): List<IconLayer> = layers.toList()
}
