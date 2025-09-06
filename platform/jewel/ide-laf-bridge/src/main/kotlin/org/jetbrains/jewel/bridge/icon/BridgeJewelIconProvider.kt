// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.icons.api.DynamicIcon
import org.jetbrains.icons.api.Icon
import org.jetbrains.jewel.ui.icon.IconPainterProvider

public object BridgeIconPainterProvider: IconPainterProvider {
    @Composable
    override fun getIconPainter(icon: Icon): Painter {
        val iconState = if (icon is DynamicIcon) {
            icon.onUpdate.collectAsState()
        } else null
        val painter = remember(icon, iconState) {
            BridgeIconPainter(icon)
        }
        return painter
    }
}

private class BridgeIconPainter(val icon: Icon) : Painter() {
    override val intrinsicSize: Size
        get() = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        val api = ComposePaintingApi(this)
        icon.render(api)
    }
}
