package org.jetbrains.jewel.ui.component

// Adapted from Icon in Compose Material package
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material/material/src/commonMain/kotlin/androidx/compose/material/Icon.kt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.icon
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.createRenderer
import com.intellij.platform.icons.scale.IconScale
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.iconRender
import org.jetbrains.jewel.ui.icon.newUiChecker
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

/**
 * Icon component that draws an icon from an [IconKey].
 *
 * @param key The [IconKey] to resolve the icon from.
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `key.iconClass`.
 * @param tint tint to be applied to the icon. If [Color.Unspecified] is provided, then no tint is applied.
 * @param hints [PainterHint]s to be used by the painter.
 */
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

/**
 * Icon component that draws an icon using IntelliJ icon designer.
 *
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param scale Scale multiplier for the icon.
 * @param iconDesigner lambda that builds an [Icon] instance using [IconDesigner].
 */
@Composable
public fun Icon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scale: IconScale? = null,
    iconDesigner: IconDesigner.() -> Unit,
) {
    Icon(icon(iconDesigner), contentDescription, modifier, scale)
}

/**
 * Icon component that draws an icon from an IntelliJ Icon.
 *
 * @param icon The Icon descriptor
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param scale Scale multiplier for the icon.
 */
@Composable
public fun Icon(icon: Icon, contentDescription: String?, modifier: Modifier = Modifier, scale: IconScale? = null) {
    val scope = rememberCoroutineScope()
    val isDark = JewelTheme.isDark

    val updateFlow = remember(scope) { IconRendererManager.createUpdateFlow(scope) }

    val context =
        remember(updateFlow, isDark) {
            IconRendererManager.createRenderingContext(
                updateFlow = updateFlow,
                defaultImageModifiers = DefaultImageModifiers(isDark = isDark),
            )
        }

    val renderer = remember(icon, context) { icon.createRenderer(context) }

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
            .iconRender(renderer, LocalDensity.current.density, scale, updateFlow)
            .then(semantics)
    )
}

/**
 * Icon component that draws an icon from an [IconKey] using a single [hint].
 *
 * @param key The [IconKey] to resolve the icon from.
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `key.iconClass`.
 * @param tint tint to be applied to the icon. If [Color.Unspecified] is provided, then no tint is applied.
 * @param hint [PainterHint] to be passed to the painter.
 */
@Suppress("ComposableParamOrder")
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

/**
 * Icon component that draws an icon from an [IconKey] using a [colorFilter].
 *
 * @param key The [IconKey] to resolve the icon from.
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `key.iconClass`.
 * @param colorFilter [ColorFilter] to be applied to the icon.
 * @param hint [PainterHint] to be passed to the painter.
 */
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

/**
 * Icon component that draws an icon from an [IconKey] using a [colorFilter].
 *
 * @param key The [IconKey] to resolve the icon from.
 * @param contentDescription text used by accessibility services to describe what this icon represents. This should
 *   always be provided unless this icon is used for decorative purposes, and does not represent a meaningful action
 *   that a user can take.
 * @param modifier optional [Modifier] for this Icon.
 * @param iconClass The class to use for resolving the icon resource. Defaults to `key.iconClass`.
 * @param colorFilter [ColorFilter] to be applied to the icon.
 * @param hints [PainterHint]s to be used by the painter.
 */
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

private fun Modifier.defaultSizeFor(painter: Painter) =
    thenIf(painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) { DefaultIconSizeModifier }

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.size(16.dp)
