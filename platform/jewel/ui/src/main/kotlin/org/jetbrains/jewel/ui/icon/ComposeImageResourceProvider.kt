// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.Density
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.icons.design.Color
import org.jetbrains.icons.modifiers.svgPatcher
import org.jetbrains.icons.patchers.SvgPatchOperation
import org.jetbrains.icons.patchers.SvgPatcher
import org.jetbrains.icons.patchers.combineWith
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceLoader
import org.jetbrains.icons.rendering.ImageResourceProvider
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.ui.painter.writeToString
import org.w3c.dom.Element

@InternalJewelApi
@ApiStatus.Internal
public class ComposeImageResourceProvider : ImageResourceProvider {
    override fun loadImage(loader: ImageResourceLoader, imageModifiers: ImageModifiers?): ImageResource {
        // TODO Support image modifiers
        if (loader is PathImageResourceLoader) {
            val extension = loader.path.substringAfterLast(".").lowercase()
            val data = loader.loadData()
            val stream = ByteArrayInputStream(data)
            return when (extension) {
                "svg" -> ComposePainterImageResource(patchSvg(imageModifiers, stream).decodeToSvgPainter(Density(1f)), imageModifiers)
                // "xml" -> loader.loadData().decodeToImageVector()
                else -> ComposeBitmapImageResource(loader.loadData().decodeToImageBitmap())
            }
        } else {
            error("Unsupported loader: $loader")
        }
    }
}

private val documentBuilderFactory =
    DocumentBuilderFactory.newDefaultInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

private fun patchSvg(modifiers: ImageModifiers?, inputStream: InputStream): ByteArray {
val builder = documentBuilderFactory.newDocumentBuilder()
    val document = builder.parse(inputStream)
    
    val knownModifiers = modifiers as? DefaultImageModifiers
    val patcher = knownModifiers?.stroke?.let { stroke ->
        svgPatcher {
            replace("fill", Color.Transparent.toHex())
            replace("stroke", stroke.toHex())
        }
    } combineWith modifiers?.svgPatcher
    patcher?.patch(document.documentElement)
    println("New SVG:")
    println(document.writeToString())
    return document.writeToString().toByteArray()
}

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
                    val matches = element.getAttribute(operation.attributeName) == operation.expectedValue
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
