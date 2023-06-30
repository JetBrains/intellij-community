package org.jetbrains.jewel

// Adapted from Icon in Compose Material package
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material/material/src/commonMain/kotlin/androidx/compose/material/Icon.kt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
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
import org.xml.sax.InputSource
import java.io.InputStream
import java.util.jar.JarFile

/**
 * Icon component that draws [imageVector] using [tint], defaulting to
 * [Color.Unspecified].
 *
 * @param imageVector [ImageVector] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be
 *     provided unless this icon is used for decorative purposes, and
 *     does not represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [imageVector]. If [Color.Unspecified]
 *     is provided, then no tint is applied
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/**
 * Icon component that draws [bitmap] using [tint], defaulting to
 * [Color.Unspecified].
 *
 * @param bitmap [ImageBitmap] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be
 *     provided unless this icon is used for decorative purposes, and
 *     does not represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [bitmap]. If [Color.Unspecified] is
 *     provided, then no tint is applied
 */
@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun Icon(
    resource: String,
    resourceLoader: ResourceLoader = LocalResourceLoader.current,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painterResource(resource, resourceLoader),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/**
 * Icon component that draws a [painter] using [tint], defaulting to
 * [Color.Unspecified]
 *
 * @param painter [Painter] to draw inside this Icon
 * @param contentDescription text used by accessibility services to
 *     describe what this icon represents. This should always be
 *     provided unless this icon is used for decorative purposes, and
 *     does not represent a meaningful action that a user can take.
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [painter]. If [Color.Unspecified] is
 *     provided, then no tint is applied
 */
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    val semantics = if (contentDescription != null) {
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
                contentScale = ContentScale.Fit
            )
            .then(semantics)
    )
}

@Composable
fun painterResource(
    resourcePath: String,
    loader: ResourceLoader
): Painter = when (resourcePath.substringAfterLast(".")) {
    "svg" -> rememberSvgResource(resourcePath, loader)
    "xml" -> rememberVectorXmlResource(resourcePath, loader)
    else -> rememberBitmapResource(resourcePath, loader)
}

@Composable
private fun rememberSvgResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default
): Painter {
    val density = LocalDensity.current
    return remember(resourcePath, density, loader) {
        useResource(resourcePath, loader) {
            loadSvgPainter(it, density)
        }
    }
}

@Composable
private fun rememberVectorXmlResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default
): Painter {
    val density = LocalDensity.current
    val image = remember(resourcePath, density, loader) {
        useResource(resourcePath, loader) {
            loadXmlImageVector(InputSource(it), density)
        }
    }
    return rememberVectorPainter(image)
}

@Composable
private fun rememberBitmapResource(
    resourcePath: String,
    loader: ResourceLoader = ResourceLoader.Default
): Painter {
    val image = remember(resourcePath) {
        useResource(resourcePath, loader, ::loadImageBitmap)
    }
    return BitmapPainter(image)
}

private inline fun <T> useResource(
    resourcePath: String,
    loader: ResourceLoader,
    block: (InputStream) -> T
): T = loader.load(resourcePath).use(block)

val LocalResourceLoader = staticCompositionLocalOf<ResourceLoader> {
    ResourceLoader.Default
}

private fun Modifier.defaultSizeFor(painter: Painter) =
    this.then(
        if (painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) {
            DefaultIconSizeModifier
        } else {
            Modifier
        }
    )

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.size(16.dp)

fun getJarPath(klass: Class<*>): String? {
    val className = klass.name.replace('.', '/') + ".class"
    val classPath = klass.classLoader.getResource(className)?.toString() ?: return null
    if (!classPath.startsWith("jar")) {
        // Class not from a JAR
        return null
    }
    return classPath.substringBefore("!").removePrefix("jar:file:")
}

fun extractFileFromJar(jarPath: String, filePath: String) =
    JarFile(jarPath).use { jar ->
        jar.getEntry(filePath)?.let { entry ->
            jar.getInputStream(entry).use { it.readBytes().inputStream() }
        }
    }

inline fun <reified T> getJarPath(): String? = getJarPath(T::class.java)

class RawJarResourceLoader(private val jars: List<String>) : ResourceLoader {

    override fun load(resourcePath: String): InputStream =
        jars.mapNotNull { jarPath ->
            extractFileFromJar(jarPath, resourcePath)
        }.firstOrNull() ?: error("Resource $resourcePath not found in jars $jars")
}
