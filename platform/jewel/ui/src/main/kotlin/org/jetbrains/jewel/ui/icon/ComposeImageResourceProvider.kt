// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.FileImageResourceLocation
import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.patchers.SvgPatchOperation
import com.intellij.platform.icons.impl.patchers.authoredStrokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.strokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.writeSvgAttribute
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.painter.writeToString
import org.w3c.dom.Element

/** An [ImageResourceProvider] that loads SVG and bitmap images using Compose painter and bitmap APIs. */
@InternalJewelApi
@ApiStatus.Internal
public class ComposeImageResourceProvider : ImageResourceProvider {
    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
        return when (location) {
            is PathImageResourceLocation ->
                load(location.path, location.resolve(imageModifiers), imageModifiers) { width, height ->
                    pathVariant(location, width, height, imageModifiers)
                }
            is UrlImageResourceLocation -> loadUrl(location.url, imageModifiers)
            // the path is resolved only here, so a relative path is kept as it is in the location
            is FileImageResourceLocation -> loadFile(location, imageModifiers)
            else -> error("Unsupported loader: $location")
        }
    }

    private fun loadFile(location: FileImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
        val url = location.toUrl()
        if (url == null) {
            // a path on a network share: images are not loaded from the network
            JewelLogger.getInstance("ComposeImageResourceProvider").warn("Cannot read the image at $location")
            return ComposePainterImageResource(EmptyPainter, imageModifiers)
        }
        return loadUrl(url, imageModifiers)
    }

    @Suppress("TooGenericExceptionCaught") // the decoders throw various exceptions for bad data
    private fun loadUrl(url: String, imageModifiers: ImageModifiers?): ImageResource {
        val resolved = resolveUrlImage(url, imageModifiers)
        if (resolved == null) {
            // e.g. a custom icon file that was moved or deleted: draw nothing instead of failing the draw
            JewelLogger.getInstance("ComposeImageResourceProvider").warn("Cannot read the image at $url")
            return ComposePainterImageResource(EmptyPainter, imageModifiers)
        }
        // the file is chosen by the user, so it can be any file: draw nothing instead of failing the draw
        // only a class loader resource has `@WxH` size variants, as for legacy icons
        return try {
            load(urlPath(url), resolved, imageModifiers, variantLoader = null)
        } catch (e: Exception) {
            JewelLogger.getInstance("ComposeImageResourceProvider").warn("Cannot decode the image at $url", e)
            ComposePainterImageResource(EmptyPainter, imageModifiers)
        }
    }

    /**
     * The image at [path], read from [resolved]. [variantLoader] loads the `@WxH` size variants of an SVG image; `null`
     * for an image without size variants.
     */
    private fun load(
        path: String,
        resolved: ResolvedImageResource,
        imageModifiers: ImageModifiers?,
        variantLoader: ((width: Int, height: Int) -> Painter?)?,
    ): ImageResource =
        when (path.substringAfterLast(".").lowercase()) {
            "svg" ->
                ComposePainterImageResource(
                    patchSvg(imageModifiers, resolved).decodeToSvgPainter(Density(1f)),
                    imageModifiers,
                    variantLoader,
                )
            // "xml" -> loader.loadData().decodeToImageVector()
            else -> ComposeBitmapImageResource(resolved.data.decodeToImageBitmap())
        }
}

/**
 * The painter of the `@WxH` size variant of the SVG image at [location], or `null` if it does not exist. A dark variant
 * without its own `_dark` file takes the light file, as the Swing renderer and the legacy icons do.
 */
private fun pathVariant(
    location: PathImageResourceLocation,
    width: Int,
    height: Int,
    imageModifiers: ImageModifiers?,
): Painter? {
    val path = SizeVariants.variantPath(location.path, width, height) ?: return null
    val variant = PathImageResourceLocation(path, location.classLoader)
    if (!variant.exists()) return null
    return decodeVariant(path, imageModifiers) { variant.resolveOrBase(imageModifiers) }
}

/**
 * The painter of the SVG image that [resolve] reads, patched for [imageModifiers], or `null` if it cannot be decoded.
 */
@Suppress("TooGenericExceptionCaught") // the decoders throw various exceptions for bad data
private fun decodeVariant(
    source: String,
    imageModifiers: ImageModifiers?,
    resolve: () -> ResolvedImageResource?,
): Painter? =
    try {
        resolve()?.let { patchSvg(imageModifiers, it).decodeToSvgPainter(Density(1f)) }
    } catch (e: Exception) {
        JewelLogger.getInstance("ComposeImageResourceProvider").warn("Cannot decode the size variant at $source", e)
        null
    }

/**
 * Reads the image at [url], picking its variant like [PathImageResourceLocation.resolve]: a stroked image prefers its
 * hand-drawn `_stroke` file and otherwise takes the light base file, a dark one prefers its `_dark` file.
 *
 * @return `null` when no file can be read, e.g. when it no longer exists
 */
internal fun resolveUrlImage(url: String, imageModifiers: ImageModifiers?): ResolvedImageResource? {
    val knownModifiers = imageModifiers as? DefaultImageModifiers
    val stroked = knownModifiers?.stroke != null
    if (stroked) {
        readUrl(variantUrl(url, "_stroke"))?.let {
            return ResolvedImageResource(it, isAuthoredStrokeVariant = true)
        }
    } else if (knownModifiers?.isDark == true) {
        readUrl(variantUrl(url, "_dark"))?.let {
            return ResolvedImageResource(it, isAuthoredStrokeVariant = false)
        }
    }
    return readUrl(url)?.let { ResolvedImageResource(it, isAuthoredStrokeVariant = false) }
}

/**
 * The path of [url], without its query and fragment, for example `file:/icons/run.svg` for `file:/icons/run.svg?v=2`.
 * The extension of the path names the image format.
 */
private fun urlPath(url: String): String = url.substringBefore('#').substringBefore('?')

/**
 * [url] with [suffix] before the extension of its file name, e.g. `file:/icons/run.svg` → `file:/icons/run_dark.svg`.
 * The suffix goes into the path of [url]: a query or a fragment, such as `#v2.0`, stays after it.
 */
private fun variantUrl(url: String, suffix: String): String {
    val path = urlPath(url)
    val queryAndFragment = url.substring(path.length)
    val nameStart = path.lastIndexOf('/') + 1
    val dot = path.lastIndexOf('.')
    val variantPath = if (dot < nameStart) path + suffix else path.substring(0, dot) + suffix + path.substring(dot)
    return variantPath + queryAndFragment
}

/**
 * The bytes at [url], or `null` when they cannot be read, for example when the file does not exist. [url] is a valid
 * URI: [UrlImageResourceLocation] accepts no other.
 */
private fun readUrl(url: String): ByteArray? =
    try {
        URI(url).toURL().openStream().use { it.readBytes() }
    } catch (_: IOException) {
        null
    }

/** Draws nothing and has no size: the image of a location that cannot be read. */
private object EmptyPainter : Painter() {
    override val intrinsicSize: Size = Size.Zero

    override fun DrawScope.onDraw() {
        // nothing to draw
    }
}

private val documentBuilderFactory =
    DocumentBuilderFactory.newDefaultInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

private fun patchSvg(modifiers: ImageModifiers?, resolved: ResolvedImageResource): ByteArray {
    val builder = documentBuilderFactory.newDocumentBuilder()
    val document = builder.parse(ByteArrayInputStream(resolved.data))

    val knownModifiers = modifiers as? DefaultImageModifiers
    // The palette substitution lives in the platform, so this frontend and the Swing one cannot drift into stroking
    // the same icon differently. Which of the two stroke patches applies follows from the file that was resolved: a
    // hand-authored stroke variant is recolored as it is, while a base icon is reduced to an outline.
    val strokePatcher =
        knownModifiers?.stroke?.let {
            if (resolved.isAuthoredStrokeVariant) authoredStrokeSvgPatcher(it) else strokeSvgPatcher(it)
        }
    // Same order as the Swing frontend: the icon's own patcher first, the stroke substitution after it. The elvis
    // keeps a stroke-only icon patched, since `svgPatcher` is null whenever an icon carries no explicit patcher.
    val patcher = modifiers?.svgPatcher?.combineWith(strokePatcher) ?: strokePatcher
    (patcher as? DefaultSvgPatcher)?.patch(document.documentElement)
    return document.writeToString().toByteArray()
}

/** The attribute's value, or `null` when the element does not carry it — DOM reports both as an empty string. */
private fun Element.attributeOrNull(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

private fun Element.writeAttribute(name: String, value: String) {
    writeSvgAttribute(name, value, { attribute, written -> setAttribute(attribute, written) }, { removeAttribute(it) })
}

@Suppress("NestedBlockDepth", "UnsafeCallOnNullableType")
private fun DefaultSvgPatcher.patch(element: Element) {
    for (operation in operations) {
        when (operation.operation) {
            SvgPatchOperation.Operation.Add -> {
                if (!element.hasAttribute(operation.attributeName)) {
                    element.writeAttribute(operation.attributeName, operation.value!!)
                }
            }
            SvgPatchOperation.Operation.Replace -> {
                // Replace never creates an attribute, conditionally or not: an element that does not carry the
                // attribute inherits it, and adding one here would override that inheritance. Add exists for that.
                if (
                    element.hasAttribute(operation.attributeName) &&
                        (!operation.conditional ||
                            operation.matches(element.attributeOrNull(operation.attributeName)) !=
                                operation.negatedCondition)
                ) {
                    element.writeAttribute(operation.attributeName, operation.value!!)
                }
            }
            SvgPatchOperation.Operation.Remove -> {
                if (operation.conditional) {
                    if (
                        operation.matches(element.attributeOrNull(operation.attributeName)) !=
                            operation.negatedCondition
                    ) {
                        element.removeAttribute(operation.attributeName)
                    }
                } else {
                    element.removeAttribute(operation.attributeName)
                }
            }
            SvgPatchOperation.Operation.Set -> element.writeAttribute(operation.attributeName, operation.value!!)
        }
    }
    val nodes = element.childNodes
    val length = nodes.length
    for (i in 0 until length) {
        val item = nodes.item(i)
        if (item is Element) {
            patch(item)
        }
    }
}
