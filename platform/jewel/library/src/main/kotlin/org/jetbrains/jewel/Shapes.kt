package org.jetbrains.jewel

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Stable
val BottomLineShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width, size.height)
        })

    override fun toString(): String = "BottomLineShape"
}

@Stable
val RightLineShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
        })

    override fun toString(): String = "RightLineShape"
}
