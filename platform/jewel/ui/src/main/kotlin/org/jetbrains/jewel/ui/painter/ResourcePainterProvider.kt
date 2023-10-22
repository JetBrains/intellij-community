package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import org.jetbrains.jewel.ui.util.inDebugMode
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private val errorPainter = ColorPainter(Color.Magenta)

/**
 * Provide [Painter] by resources in the module and jars, it use the
 * ResourceResolver to load resources.
 *
 * It will cache the painter by [PainterHint]s, so it is safe to call
 * [getPainter] multiple times.
 *
 * If a resource fails to load, it will be silently replaced by a
 * magenta color painter, and the exception logged. If Jewel is in
 * [debug mode][inDebugMode], however, exceptions will not be suppressed.
 */
@Immutable
class ResourcePainterProvider(
    private val basePath: String,
    vararg classLoaders: ClassLoader,
) : PainterProvider {

    private val cache = ConcurrentHashMap<Int, Painter>()

    private val contextClassLoaders = classLoaders.toList()

    private val documentBuilderFactory = DocumentBuilderFactory.newDefaultInstance()
        .apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

    @Composable
    override fun getPainter(vararg hints: PainterHint): State<Painter> {
        val currentHintsProvider = LocalPainterHintsProvider.current
        val providedHints =
            currentHintsProvider.priorityHints(basePath) + hints.toList() + currentHintsProvider.hints(basePath)
        val resolvedHints = providedHints.filter { it.canApplyTo(basePath) }

        val cacheKey = resolvedHints.hashCode()

        if (inDebugMode && cache[cacheKey] != null) {
            println("Cache hit for $basePath(${resolvedHints.joinToString()})")
        }

        val painter = cache.getOrPut(cacheKey) {
            if (inDebugMode) {
                println("Cache miss for $basePath(${resolvedHints.joinToString()})")
            }
            loadPainter(resolvedHints)
        }

        return rememberUpdatedState(painter)
    }

    @Composable
    private fun loadPainter(hints: List<PainterHint>): Painter {
        var paths = setOf(basePath)

        for (hint in hints) {
            if (hint !is PainterResourcePathHint && hint !is PainterPathHint) continue
            paths = paths.flatMap {
                val patched = when (hint) {
                    is PainterResourcePathHint -> hint.patch(it, contextClassLoaders)
                    is PainterPathHint -> hint.patch(it)
                    else -> return@flatMap listOf(it)
                }

                setOf(patched, it)
            }.toSet()
        }

        val url = paths.firstNotNullOfOrNull { resolveResource(it) }

        @Suppress("UrlHashCode") // It's ok when comparing a URL to null
        if (url == null) {
            if (inDebugMode) {
                error("Resource '$basePath(${hints.joinToString()})' not found")
            } else {
                return errorPainter
            }
        }

        val density = LocalDensity.current

        val extension = basePath.substringAfterLast(".").lowercase()

        var painter = when (extension) {
            "svg" -> createSvgPainter(url, density, hints)
            "xml" -> createVectorDrawablePainter(url, density)
            else -> createBitmapPainter(url, density)
        }

        for (hint in hints) {
            if (hint !is PainterWrapperHint) continue
            painter = hint.wrap(painter)
        }

        return painter
    }

    private fun resolveResource(path: String): URL? {
        val normalized = path.removePrefix("/")

        for (classLoader in contextClassLoaders) {
            val url = classLoader.getResource(normalized)
            if (url != null) {
                if (inDebugMode) println("Found resource: '$normalized'")
                return url
            }
        }

        return null
    }

    @Composable
    private fun createSvgPainter(url: URL, density: Density, hints: List<PainterHint>): Painter =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                patchSvg(url.openStream(), hints).use { inputStream ->
                    if (inDebugMode) {
                        println("Loading icon $basePath(${hints.joinToString()}) from $resourceUrl")
                    }
                    loadSvgPainter(inputStream, density)
                }
            },
            rememberAction = { remember(url, density, hints) { it } },
        )

    private fun patchSvg(inputStream: InputStream, hints: List<PainterHint>): InputStream {
        if (hints.all { it !is PainterSvgPatchHint }) {
            return inputStream
        }

        inputStream.use {
            val builder = documentBuilderFactory.newDocumentBuilder()
            val document = builder.parse(inputStream)

            hints.forEach { hint ->
                if (hint !is PainterSvgPatchHint) return@forEach
                hint.patch(document.documentElement)
            }

            return document.writeToString().byteInputStream()
        }
    }

    @Composable
    private fun createVectorDrawablePainter(url: URL, density: Density): Painter =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                resourceUrl.openStream().use {
                    loadXmlImageVector(InputSource(it), density)
                }
            },
            rememberAction = { rememberVectorPainter(it) },
        )

    @Composable
    private fun createBitmapPainter(url: URL, density: Density) =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                val bitmap = resourceUrl.openStream().use {
                    loadImageBitmap(it)
                }
                BitmapPainter(bitmap)
            },
            rememberAction = { remember(url, density) { it } },
        )

    @Composable
    private fun <T> tryLoadingResource(
        url: URL,
        loadingAction: (URL) -> T,
        rememberAction: @Composable (T) -> Painter,
    ): Painter {
        @Suppress("TooGenericExceptionCaught") // This is a last-resort fallback when icons fail to load
        val painter = try {
            loadingAction(url)
        } catch (e: RuntimeException) {
            val message = "Unable to load SVG resource from $url\n${e.stackTraceToString()}"
            if (inDebugMode) {
                error(message)
            }

            System.err.println(message)
            return errorPainter
        }

        return rememberAction(painter)
    }
}

internal fun Document.writeToString(): String {
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

@Composable
fun rememberResourcePainterProvider(path: String, iconClass: Class<*>): PainterProvider =
    remember(path, iconClass.classLoader) {
        ResourcePainterProvider(path, iconClass.classLoader)
    }
