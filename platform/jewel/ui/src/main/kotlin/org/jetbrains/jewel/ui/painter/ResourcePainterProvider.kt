package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.w3c.dom.Document

private val errorPainter = ColorPainter(Color.Magenta)

/**
 * Provide [Painter] by resources in the module and jars, it use the ResourceResolver to load resources.
 *
 * It will cache the painter by [PainterHint]s, so it is safe to call [getPainter] multiple times.
 *
 * If a resource fails to load, it will be silently replaced by a magenta color painter, and the exception logged as
 * error.
 */
public class ResourcePainterProvider(private val basePath: String, vararg classLoaders: ClassLoader) : PainterProvider {
    private val logger = myLogger()

    private val classLoaders = classLoaders.toSet()

    private val cache = ConcurrentHashMap<Int, Painter>()

    private val contextClassLoaders = classLoaders.toList()

    private val documentBuilderFactory =
        DocumentBuilderFactory.newDefaultInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }

    private fun Scope.resolveHint(hint: PainterHint) {
        with(hint) {
            if (canApply()) {
                acceptedHints += hint
            }
        }
    }

    @Composable
    override fun getPainter(vararg hints: PainterHint): State<Painter> {
        val density = LocalDensity.current
        val scope = Scope(density, basePath, classLoaders)

        val currentHintsProvider = LocalPainterHintsProvider.current
        currentHintsProvider.priorityHints(basePath).forEach { scope.resolveHint(it) }

        hints.forEach { scope.resolveHint(it) }

        currentHintsProvider.hints(basePath).forEach { scope.resolveHint(it) }

        val cacheKey = scope.acceptedHints.hashCode() * 31 + LocalDensity.current.hashCode()

        if (cache[cacheKey] != null) {
            logger.trace("Cache hit for $basePath (accepted hints: ${scope.acceptedHints.joinToString()})")
        }

        val painter =
            cache.getOrPut(cacheKey) {
                logger.trace("Cache miss for $basePath (accepted hints: ${scope.acceptedHints.joinToString()})")
                loadPainter(scope)
            }

        return rememberUpdatedState(painter)
    }

    @Composable
    private fun loadPainter(scope: Scope): Painter {
        var scopes = listOf(scope)

        for (hint in scope.acceptedHints) {
            if (hint !is PainterPathHint) continue
            scopes = scopes.flatMap { listOfNotNull(it.apply(hint), it) }
        }

        val (chosenScope, url) =
            scopes.firstNotNullOfOrNull { resolveResource(it) }
                ?: run {
                    logger.error("Resource '$basePath(${scope.acceptedHints.joinToString()})' not found")
                    return errorPainter
                }

        val extension = basePath.substringAfterLast(".").lowercase()

        var painter =
            when (extension) {
                "svg" -> createSvgPainter(chosenScope, url)
                "xml" -> createVectorDrawablePainter(chosenScope, url)
                else -> createBitmapPainter(url)
            }

        for (hint in scope.acceptedHints) {
            if (hint !is PainterWrapperHint) continue
            with(hint) { painter = chosenScope.wrap(painter) }
        }

        return painter
    }

    private fun resolveResource(scope: Scope): Pair<Scope, URL>? {
        val normalized = scope.path.removePrefix("/")

        for (classLoader in contextClassLoaders) {
            val url = classLoader.getResource(normalized)
            if (url != null) {
                logger.trace("Found resource: '$normalized'")
                return scope to url
            }
        }

        return null
    }

    @OptIn(ExperimentalResourceApi::class)
    @Composable
    private fun createSvgPainter(scope: Scope, url: URL): Painter =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                patchSvg(scope, url.openStream(), scope.acceptedHints).use { inputStream ->
                    logger.trace("Loading icon $basePath(${scope.acceptedHints.joinToString()}) from $resourceUrl")
                    inputStream.readAllBytes().decodeToSvgPainter(scope)
                }
            },
            paintAction = { it },
        )

    private fun patchSvg(scope: Scope, inputStream: InputStream, hints: List<PainterHint>): InputStream {
        if (hints.all { it !is PainterSvgPatchHint }) {
            return inputStream
        }

        inputStream.use {
            val builder = documentBuilderFactory.newDocumentBuilder()
            val document = builder.parse(inputStream)

            hints.forEach { hint ->
                if (hint !is PainterSvgPatchHint) return@forEach
                with(hint) { scope.patch(document.documentElement) }
            }

            return document
                .writeToString()
                .also { patchedSvg -> logger.trace("Patched SVG:\n\n$patchedSvg") }
                .byteInputStream()
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    @Composable
    private fun createVectorDrawablePainter(scope: Scope, url: URL): Painter =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                resourceUrl.openStream().use { inputStream -> inputStream.readAllBytes().decodeToImageVector(scope) }
            },
            paintAction = { rememberVectorPainter(it) },
        )

    @OptIn(ExperimentalResourceApi::class)
    @Composable
    private fun createBitmapPainter(url: URL) =
        tryLoadingResource(
            url = url,
            loadingAction = { resourceUrl ->
                val bitmap = resourceUrl.openStream().use { it.readAllBytes().decodeToImageBitmap() }
                BitmapPainter(bitmap)
            },
            paintAction = { it },
        )

    @Composable
    private fun <T> tryLoadingResource(
        url: URL,
        loadingAction: (URL) -> T,
        paintAction: @Composable (T) -> Painter,
    ): Painter {
        @Suppress("TooGenericExceptionCaught") // This is a last-resort fallback when icons fail to load
        val painter =
            try {
                loadingAction(url)
            } catch (e: RuntimeException) {
                val message = "Unable to load resource from $url\n${e.stackTraceToString()}"
                logger.error(message)
                return errorPainter
            }

        return paintAction(painter)
    }

    private class Scope(
        private val localDensity: Density,
        override val rawPath: String,
        override val classLoaders: Set<ClassLoader>,
        override val path: String = rawPath,
        override val acceptedHints: MutableList<PainterHint> = mutableListOf(),
    ) : ResourcePainterProviderScope, Density by localDensity {
        fun apply(pathHint: PainterPathHint): Scope? {
            with(pathHint) {
                val patched = patch()
                if (patched == path) {
                    return null
                }
                return Scope(
                    localDensity = localDensity,
                    rawPath = rawPath,
                    classLoaders = classLoaders,
                    path = patched,
                    acceptedHints = acceptedHints,
                )
            }
        }
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
public fun rememberResourcePainterProvider(
    iconKey: IconKey,
    iconClass: Class<*> = iconKey::class.java,
): PainterProvider {
    val isNewUi = LocalNewUiChecker.current.isNewUi()
    return remember(iconKey, iconClass.classLoader, isNewUi) {
        ResourcePainterProvider(iconKey.path(isNewUi), iconClass.classLoader)
    }
}

@Composable
public fun rememberResourcePainterProvider(path: String, iconClass: Class<*>): PainterProvider =
    remember(path, iconClass.classLoader) { ResourcePainterProvider(path, iconClass.classLoader) }
