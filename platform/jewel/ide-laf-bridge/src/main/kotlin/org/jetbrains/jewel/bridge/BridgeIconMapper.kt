package org.jetbrains.jewel.bridge

import androidx.compose.ui.res.ResourceLoader
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ClassLoaderProvider
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.InternalJewelApi

internal object BridgeIconMapper : IconMapper {

    private val logger = thisLogger()

    private val dirProvider = DirProvider()

    @OptIn(InternalJewelApi::class)
    override fun mapPath(
        originalPath: String,
        iconData: IntelliJThemeIconData,
        resourceLoader: ResourceLoader,
    ): String {
        val classLoaders = (resourceLoader as? ClassLoaderProvider)?.classLoaders
        if (classLoaders == null) {
            logger.warn(
                "Tried loading a resource but the provided ResourceLoader is now a JewelResourceLoader; " +
                    "this is probably a bug. Make sure you always use JewelResourceLoaders.",
            )
            return originalPath
        }

        val patchedPath = getPatchedIconPath(dirProvider, originalPath, classLoaders)
        val path = if (patchedPath != null) {
            logger.info("Found icon mapping: '$originalPath' -> '$patchedPath'")
            patchedPath
        } else {
            logger.debug("Icon '$originalPath' has no available mapping")
            originalPath
        }

        val overriddenPath = iconData.iconOverrides[path] ?: path
        if (overriddenPath != path) {
            logger.info("Found theme icon override: '$path' -> '$overriddenPath'")
        }

        return overriddenPath
    }
}
