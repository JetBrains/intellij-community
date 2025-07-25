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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.newUiChecker
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

@Suppress("ComposableParamOrder") // It doesn't like the vararg
@Composable
public fun Icon(
    key: IconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = key.iconClass,
    tint: Color = Color.Unspecified,
    vararg hints: PainterHint,
) {
    val isNewUi = JewelTheme.newUiChecker.isNewUi()
    val path = remember(key, isNewUi) { key.path(isNewUi) }
    val painterProvider = rememberResourcePainterProvider(path, iconClass)
    val painter by painterProvider.getPainter(*hints)

    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-929
@Composable
public fun Icon(
    key: IconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = key.iconClass,
    tint: Color = Color.Unspecified,
    hint: PainterHint,
) {
    val isNewUi = JewelTheme.newUiChecker.isNewUi()
    val path = remember(key, isNewUi) { key.path(isNewUi) }

    val painterProvider = rememberResourcePainterProvider(path, iconClass)
    val painter by painterProvider.getPainter(hint)

    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-929
@Composable
public fun Icon(
    key: IconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = key.iconClass,
    colorFilter: ColorFilter?,
    hint: PainterHint,
) {
    val isNewUi = JewelTheme.newUiChecker.isNewUi()
    val path = remember(key, isNewUi) { key.path(isNewUi) }
    val painterProvider = rememberResourcePainterProvider(path, iconClass)
    val painter by painterProvider.getPainter(hint)

    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, colorFilter = colorFilter)
}

@Suppress("ComposableParamOrder") // To fix in JEWEL-929
@Composable
public fun Icon(
    key: IconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconClass: Class<*> = key.iconClass,
    colorFilter: ColorFilter?,
    vararg hints: PainterHint,
) {
    val isNewUi = JewelTheme.newUiChecker.isNewUi()
    val path = remember(key, isNewUi) { key.path(isNewUi) }
    val painterProvider = rememberResourcePainterProvider(path, iconClass)
    val painter by painterProvider.getPainter(*hints)

    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, colorFilter = colorFilter)
}

/**
 * Icon component that draws [imageVector] using [tint], defaulting to [Color.Unspecified].
 *
 * @param imageVector [ImageVector] to draw inside this Icon
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [imageVector]. If [Color.Unspecified] is provided, then no tint is applied
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
 * Icon component that draws [bitmap] using [tint], defaulting to [Color.Unspecified].
 *
 * @param bitmap [ImageBitmap] to draw inside this Icon
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [bitmap]. If [Color.Unspecified] is provided, then no tint is applied
 */
@Composable
public fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/**
 * Icon component that draws a [painter] using [tint], defaulting to [Color.Unspecified]
 *
 * @param painter [Painter] to draw inside this Icon
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [painter]. If [Color.Unspecified] is provided, then no tint is applied
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
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
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
        modifier
            .toolingGraphicsLayer()
            .defaultSizeFor(painter)
            .paint(painter, colorFilter = colorFilter, contentScale = ContentScale.Fit)
            .then(semantics)
    )
}

@Composable
public fun painterResource(resourcePath: String): Painter =
    when (resourcePath.substringAfterLast(".").lowercase()) {
        "svg" -> rememberSvgResource(resourcePath)
        "xml" -> rememberVectorXmlResource(resourcePath)
        else -> rememberBitmapResource(resourcePath)
    }

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberSvgResource(path: String): Painter {
    val density = LocalDensity.current
    return remember(density, path) { readResourceBytes(path).decodeToSvgPainter(density) }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberVectorXmlResource(path: String): Painter {
    val density = LocalDensity.current
    val imageVector = remember(density, path) { readResourceBytes(path).decodeToImageVector(density) }
    return rememberVectorPainter(imageVector)
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberBitmapResource(path: String): Painter =
    remember(path) { BitmapPainter(readResourceBytes(path).decodeToImageBitmap()) }

private object ResourceLoader

private fun readResourceBytes(resourcePath: String) =
    checkNotNull(ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Could not load resource $resourcePath: it does not exist or can't be read."
        }
        .readAllBytes()

private fun Modifier.defaultSizeFor(painter: Painter) =
    thenIf(painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) { DefaultIconSizeModifier }

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.size(16.dp)
