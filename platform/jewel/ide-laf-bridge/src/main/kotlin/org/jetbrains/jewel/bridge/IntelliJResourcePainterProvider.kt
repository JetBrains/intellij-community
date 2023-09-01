package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader
import com.intellij.ide.ui.IconMapLoader
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.JewelResourceLoader
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.ResourcePainterProvider

class IntelliJResourcePainterProvider<T : InteractiveComponentState>(
    basePath: String,
    svgLoader: SvgLoader,
    prefixTokensProvider: (state: T) -> String = { "" },
    suffixTokensProvider: (state: T) -> String = { "" },
) : ResourcePainterProvider<T>(basePath, svgLoader, prefixTokensProvider, suffixTokensProvider) {

    private val logger = thisLogger()

    private val mappingsByClassLoader
        get() = service<IconMapLoader>().loadIconMapping()

    @Composable
    override fun patchPath(state: T, basePath: String, resourceLoader: ResourceLoader): String {
        val patchedPath = super.patchPath(state, basePath, resourceLoader)
        return mapPath(patchedPath, resourceLoader)
    }

    private fun mapPath(originalPath: String, resourceLoader: ResourceLoader): String {
        logger.debug("Loading SVG from '$originalPath'")
        val searchClasses = (resourceLoader as? JewelResourceLoader)?.searchClasses
        if (searchClasses == null) {
            logger.warn(
                "Tried loading a resource but the provided ResourceLoader is now a JewelResourceLoader; " +
                    "this is probably a bug. Make sure you always use JewelResourceLoaders.",
            )
            return originalPath
        }

        val allMappings = mappingsByClassLoader
        if (allMappings.isEmpty()) {
            logger.info("No mapping info available yet, can't check for '$originalPath' mapping.")
            return originalPath
        }

        val applicableMappings = searchClasses.mapNotNull { allMappings[it.classLoader] }
        val mappedPath = applicableMappings.firstNotNullOfOrNull { it[originalPath.removePrefix("/")] }

        if (mappedPath == null) {
            logger.debug("Icon '$originalPath' has no mapping defined.")
            return originalPath
        }

        logger.debug("Icon '$originalPath' is mapped to '$mappedPath'.")
        return mappedPath
    }
}
