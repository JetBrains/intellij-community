package org.jetbrains.jewel.themes.intui.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.PaletteMapper
import org.jetbrains.jewel.SvgPatcher
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.roundToInt

@Immutable
class IntelliJSvgPatcher(private val mapper: PaletteMapper) : SvgPatcher {

    private val documentBuilderFactory = DocumentBuilderFactory.newDefaultInstance()
        .apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

    override fun patchSvg(rawSvg: InputStream, path: String?): String {
        val builder = documentBuilderFactory.newDocumentBuilder()
        val document = builder.parse(rawSvg)

        val scope = mapper.getScopeForPath(path)
        if (scope != null) {
            document.documentElement.patchColors(scope)
        }

        return document.writeToString()
    }

    private fun Element.patchColors(mapperScope: PaletteMapper.Scope) {
        patchColorAttribute("fill", mapperScope)
        patchColorAttribute("stroke", mapperScope)

        val nodes = childNodes
        val length = nodes.length
        for (i in 0 until length) {
            val item = nodes.item(i)
            if (item is Element) {
                item.patchColors(mapperScope)
            }
        }
    }

    private fun Element.patchColorAttribute(attrName: String, mapperScope: PaletteMapper.Scope) {
        val color = getAttribute(attrName)
        val opacity = getAttribute("$attrName-opacity")

        if (color.isNotEmpty()) {
            val alpha = opacity.toFloatOrNull() ?: 1.0f
            val originalColor = tryParseColor(color, alpha) ?: return
            val newColor = mapperScope.mapColorOrNull(originalColor) ?: return
            setAttribute(attrName, newColor.copy(alpha = alpha).toHexString())
        }
    }

    private fun Color.toHexString(): String {
        val r = Integer.toHexString((red * 255).roundToInt())
        val g = Integer.toHexString((green * 255).roundToInt())
        val b = Integer.toHexString((blue * 255).roundToInt())

        return buildString {
            append('#')
            append(r.padStart(2, '0'))
            append(g.padStart(2, '0'))
            append(b.padStart(2, '0'))

            if (alpha != 1.0f) {
                val a = Integer.toHexString((alpha * 255).roundToInt())
                append(a.padStart(2, '0'))
            }
        }
    }

    private fun tryParseColor(color: String, alpha: Float): Color? {
        val rawColor = color.lowercase()
        if (rawColor.startsWith("#") && rawColor.length - 1 <= 8) {
            return fromHexOrNull(rawColor, alpha)
        }
        return null
    }

    private fun fromHexOrNull(rawColor: String, alpha: Float): Color? {
        val startPos = if (rawColor.startsWith("#")) 1 else if (rawColor.startsWith("0x")) 2 else 0
        val length = rawColor.length - startPos
        val alphaOverride = alpha.takeIf { it != 1.0f }?.let { (it * 255).roundToInt() }

        return when (length) {
            3 -> Color(
                red = rawColor.substring(startPos, startPos + 1).toInt(16),
                green = rawColor.substring(startPos + 1, startPos + 2).toInt(16),
                blue = rawColor.substring(startPos + 2, startPos + 3).toInt(16),
                alpha = alphaOverride ?: 255,
            )

            4 -> Color(
                red = rawColor.substring(startPos, startPos + 1).toInt(16),
                green = rawColor.substring(startPos + 1, startPos + 2).toInt(16),
                blue = rawColor.substring(startPos + 2, startPos + 3).toInt(16),
                alpha = alphaOverride ?: rawColor.substring(startPos + 3, startPos + 4).toInt(16),
            )

            6 -> Color(
                red = rawColor.substring(startPos, startPos + 2).toInt(16),
                green = rawColor.substring(startPos + 2, startPos + 4).toInt(16),
                blue = rawColor.substring(startPos + 4, startPos + 6).toInt(16),
                alpha = alphaOverride ?: 255,
            )

            8 -> Color(
                red = rawColor.substring(startPos, startPos + 2).toInt(16),
                green = rawColor.substring(startPos + 2, startPos + 4).toInt(16),
                blue = rawColor.substring(startPos + 4, startPos + 6).toInt(16),
                alpha = alphaOverride ?: rawColor.substring(startPos + 6, startPos + 8).toInt(16),
            )

            else -> null
        }
    }

    private fun Document.writeToString(): String {
        val tf = TransformerFactory.newInstance()
        val transformer: Transformer

        try {
            transformer = tf.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

            val writer = StringWriter()
            transformer.transform(DOMSource(this), StreamResult(writer))
            return writer.buffer.toString()
        } catch (e: TransformerException) {
            error("Unable to render XML document to string: ${e.message}")
        } catch (e: IOException) {
            error("Unable to render XML document to string: ${e.message}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntelliJSvgPatcher

        return mapper == other.mapper
    }

    override fun hashCode(): Int = mapper.hashCode()

    override fun toString(): String = "IntelliJSvgPatcher(mapper=$mapper)"
}
