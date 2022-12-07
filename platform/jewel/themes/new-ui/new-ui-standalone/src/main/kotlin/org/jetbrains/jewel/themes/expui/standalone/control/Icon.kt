package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toolingGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.theme.LocalIsDarkTheme

@Composable
fun themedSvgResource(resource: String, isDark: Boolean = LocalIsDarkTheme.current): Painter {
    var realResource = resource
    if (isDark) {
        if (!realResource.endsWith("_dark.svg")) {
            val dark = realResource.replace(".svg", "_dark.svg")
            if (Thread.currentThread().contextClassLoader.getResource(dark) != null) {
                realResource = dark
            }
        }
    } else {
        if (realResource.endsWith("_dark.svg")) {
            val light = realResource.replace("_dark.svg", ".svg")
            if (Thread.currentThread().contextClassLoader.getResource(light) != null) {
                realResource = light
            }
        }
    }
    return painterResource(realResource)
}

private fun Modifier.defaultSizeFor(painter: Painter) = this.then(
    if (painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) {
        DefaultIconSizeModifier
    } else {
        Modifier
    }
)

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

private val DefaultIconSizeModifier = Modifier.size(20.dp)

@Composable
fun Icon(
    resource: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    markerColor: Color = Color.Unspecified
) {
    Icon(
        themedSvgResource(resource),
        contentDescription,
        modifier,
        colorFilter = colorFilter,
        markerColor = markerColor
    )
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    markerColor: Color = Color.Unspecified
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = colorFilter,
        markerColor = markerColor
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    markerColor: Color = Color.Unspecified
) {
    Icon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = colorFilter,
        markerColor = markerColor
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    markerColor: Color = Color.Unspecified
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        Modifier
    }
    val filter = colorFilter ?: run {
        val foreground = LocalAreaColors.current.foreground
        if (foreground.isSpecified) {
            ColorFilter.tint(foreground)
        } else {
            null
        }
    }
    Box(
        modifier.toolingGraphicsLayer()
            .defaultSizeFor(painter)
            .paintWithMarker(painter, contentScale = ContentScale.None, colorFilter = filter, markerColor = markerColor)
            .then(semantics)
    )
}
