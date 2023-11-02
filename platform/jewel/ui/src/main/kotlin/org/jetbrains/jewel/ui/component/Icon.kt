package org.jetbrains.jewel.ui.component

// Adapted from Icon in Compose Material package
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material/material/src/commonMain/kotlin/androidx/compose/material/Icon.kt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.util.thenIf
import org.xml.sax.InputSource
import java.io.InputStream

@Composable
public fun Icon(
    resource: String,
    contentDescription: String?,
    iconClass: Class<*>,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    val painterProvider = rememberResourcePainterProvider(resource, iconClass)
    val painter by painterProvider.getPainter()

    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = colorFilter,
    )
}

@Composable
public fun Icon(
    resource: String,
    contentDescription: String?,
    iconClass: Class<*>,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val painterProvider = rememberResourcePainterProvider(resource, iconClass)
    val painter by painterProvider.getPainter()

    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * Icon component that draws [imageVector] using [tint], defaulting to
 * [Color.Unspecified].
 *
 * @param imageVector [ImageVector] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be provided
 *     unless this icon is used for decorative purposes, and does not
 *     represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [imageVector]. If [Color.Unspecified]
 *     is provided, then no tint is applied
 */
@Composable
public fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * Icon component that draws [bitmap] using [tint], defaulting to
 * [Color.Unspecified].
 *
 * @param bitmap [ImageBitmap] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be provided
 *     unless this icon is used for decorative purposes, and does not
 *     represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [bitmap]. If [Color.Unspecified] is
 *     provided, then no tint is applied
 */
@Composable
public fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * Icon component that draws a [painter] using [tint], defaulting to
 * [Color.Unspecified]
 *
 * @param painter [Painter] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be provided
 *     unless this icon is used for decorative purposes, and does not
 *     represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [painter]. If [Color.Unspecified] is
 *     provided, then no tint is applied
 */
@Composable
public fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
    Icon(painter, contentDescription, colorFilter, modifier)
}

/**
 * Icon component that draws a [painter] using a [colorFilter]
 *
 * @param painter [Painter] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be provided
 *     unless this icon is used for decorative purposes, and does not
 *     represent a meaningful action that a user can take.
 * @param colorFilter color filter to be applied to [painter]
 * @param modifier optional [Modifier] for this Icon
 */
@Composable
public fun Icon(
    painter: Painter,
    contentDescription: String?,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    val semantics =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else {
            Modifier
        }
    Box(
        modifier.toolingGraphicsLayer()
            .defaultSizeFor(painter)
            .paint(
                painter,
                colorFilter = colorFilter,
                contentScale = ContentScale.Fit,
            )
            .then(semantics),
    )
}

@Composable
public fun painterResource(
    resourcePath: String,
    loader: ResourceLoader,
): Painter =
    when (resourcePath.substringAfterLast(".").lowercase()) {
        "svg" -> rememberSvgResource(resourcePath, loader)
        "xml" -> rememberVectorXmlResource(resourcePath, loader)
        else -> rememberBitmapResource(resourcePath, loader)
    }

@Composable
private fun rememberSvgResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default,
): Painter {
    val density = LocalDensity.current
    return remember(resourcePath, density, loader) {
        useResource(resourcePath, loader) { loadSvgPainter(it, density) }
    }
}

@Composable
private fun rememberVectorXmlResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default,
): Painter {
    val density = LocalDensity.current
    val image = remember(resourcePath, density, loader) {
        useResource(resourcePath, loader) { loadXmlImageVector(InputSource(it), density) }
    }
    return rememberVectorPainter(image)
}

@Composable
private fun rememberBitmapResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default,
): Painter {
    val image = remember(resourcePath) { useResource(resourcePath, loader, ::loadImageBitmap) }
    return BitmapPainter(image)
}

private inline fun <T> useResource(
    resourcePath: String,
    loader: ResourceLoader,
    block: (InputStream) -> T,
): T = loader.load(resourcePath).use(block)

private fun Modifier.defaultSizeFor(painter: Painter) =
    thenIf(painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) {
        DefaultIconSizeModifier
    }

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.size(16.dp)
