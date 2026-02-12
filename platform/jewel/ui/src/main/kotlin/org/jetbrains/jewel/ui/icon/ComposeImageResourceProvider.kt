// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.Density
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.modifiers.svgPatcher
import org.jetbrains.icons.patchers.SvgPatchOperation
import org.jetbrains.icons.patchers.SvgPatcher
import org.jetbrains.icons.patchers.combineWith
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.rendering.ImageResourceProvider
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.ui.painter.writeToString
import org.w3c.dom.Element

@OptIn(ExperimentalIconsApi::class)
@InternalJewelApi
@ApiStatus.Internal
@InternalIconsApi
public class ComposeImageResourceProvider : ImageResourceProvider {
    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
        // TODO Support image modifiers
        if (location is PathImageResourceLocation) {
            val extension = location.path.substringAfterLast(".").lowercase()
            val data = location.loadData(imageModifiers)
            val stream = ByteArrayInputStream(data)
            return when (extension) {
                "svg" -> ComposePainterImageResource(patchSvg(imageModifiers, stream).decodeToSvgPainter(Density(1f)), imageModifiers)
                // "xml" -> loader.loadData().decodeToImageVector()
                else -> ComposeBitmapImageResource(location.loadData(imageModifiers).decodeToImageBitmap())
            }
        } else {
            error("Unsupported loader: $location")
        }
    }
}

private val documentBuilderFactory =
    DocumentBuilderFactory.newDefaultInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

@InternalIconsApi
private fun patchSvg(modifiers: ImageModifiers?, inputStream: InputStream): ByteArray {
val builder = documentBuilderFactory.newDocumentBuilder()
    val document = builder.parse(inputStream)
    
    val knownModifiers = modifiers as? DefaultImageModifiers
    val patcher = knownModifiers?.stroke?.let { stroke ->
        svgPatcher {
            for (color in backgroundPalette) {
                replaceIfMatches("fill", color.toIconsColor().toHex(), "transparent")
            }
            for (color in strokeColors) {
                replaceIfMatches("fill", color.toIconsColor().toHex(), stroke.toHex())
            }
        }
    } combineWith modifiers?.svgPatcher
    patcher?.patch(document.documentElement)
    return document.writeToString().toByteArray()
}

private val backgroundPalette =
    listOf(
        Color(0xFFEBECF0),
        Color(0xFFE7EFFD),
        Color(0xFFDFF2E0),
        Color(0xFFF2FCF3),
        Color(0xFFFFE8E8),
        Color(0xFFFFF5F5),
        Color(0xFFFFF8E3),
        Color(0xFFFFF4EB),
        Color(0xFFEEE0FF),
    )

private val strokeColors =
    listOf(
        Color(0xFF000000),
        Color(0xFFFFFFFF),
        Color(0xFF818594),
        Color(0xFF6C707E),
        Color(0xFF3574F0),
        Color(0xFF5FB865),
        Color(0xFFE35252),
        Color(0xFFEB7171),
        Color(0xFFE3AE4D),
        Color(0xFFFCC75B),
        Color(0xFFF28C35),
        Color(0xFF955AE0),
    )

private fun SvgPatcher.patch(element: Element) {
    for (operation in operations) {
        when (operation.operation) {
            SvgPatchOperation.Operation.Add -> {
                if (!element.hasAttribute(operation.attributeName)) {
                    element.setAttribute(operation.attributeName, operation.value!!)
                }
            }
            SvgPatchOperation.Operation.Replace -> {
                if (operation.conditional) {
                    val matches =
                        element.getAttribute(operation.attributeName).equals(operation.expectedValue, ignoreCase = true)
                    if (matches == !operation.negatedCondition) {
                        element.setAttribute(operation.attributeName, operation.value!!)
                    }
                } else if (element.hasAttribute(operation.attributeName)) {
                    element.setAttribute(operation.attributeName, operation.value!!)
                }
            }
            SvgPatchOperation.Operation.Remove -> {
                if (operation.conditional) {
                    val matches = element.getAttribute(operation.attributeName) == operation.expectedValue
                    if (matches == !operation.negatedCondition) {
                        element.removeAttribute(operation.attributeName)
                    }
                } else {
                    element.removeAttribute(operation.attributeName)
                }
            }
            SvgPatchOperation.Operation.Set -> element.setAttribute(operation.attributeName, operation.value!!)
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
