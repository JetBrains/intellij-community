// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.Density
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.patchers.SvgPatchOperation
import com.intellij.platform.icons.impl.patchers.authoredStrokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.strokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.writeSvgAttribute
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.ui.painter.writeToString
import org.w3c.dom.Element

/** An [ImageResourceProvider] that loads SVG and bitmap images using Compose painter and bitmap APIs. */
@InternalJewelApi
@ApiStatus.Internal
public class ComposeImageResourceProvider : ImageResourceProvider {
    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
        if (location is PathImageResourceLocation) {
            val extension = location.path.substringAfterLast(".").lowercase()
            val resolved = location.resolve(imageModifiers)
            return when (extension) {
                "svg" ->
                    ComposePainterImageResource(
                        patchSvg(imageModifiers, resolved).decodeToSvgPainter(Density(1f)),
                        imageModifiers,
                    )
                // "xml" -> loader.loadData().decodeToImageVector()
                else -> ComposeBitmapImageResource(resolved.data.decodeToImageBitmap())
            }
        } else {
            error("Unsupported loader: $location")
        }
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
