package org.jetbrains.jewel.bridge

import androidx.compose.ui.res.ResourceLoader
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ClassLoaderProvider
import org.jetbrains.jewel.IntelliJThemeIconData

internal object BridgeIconMapper : IconMapper {

    private val logger = thisLogger()

    private val dirProvider = DirProvider()

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

        // TODO #116 replace with public API access once it's made available (IJP 233?)
        val clazz = Class.forName("com.intellij.ui.icons.CachedImageIconKt")
        val patchIconPath = clazz.getMethod("patchIconPath", String::class.java, ClassLoader::class.java)
        patchIconPath.isAccessible = true

        // For all provided classloaders, we try to get the patched path, both using
        // the original path, and an "abridged" path that has gotten the icon path prefix
        // removed (the classloader is set up differently in prod IDEs and when running
        // from Gradle, and the icon could be in either place depending on the environment)
        val fallbackPath = originalPath.removePrefix(dirProvider.dir())
        val patchedPath = classLoaders.firstNotNullOfOrNull { classLoader ->
            val patchedPathAndClassLoader =
                patchIconPath.invoke(null, originalPath.removePrefix("/"), classLoader)
                    ?: patchIconPath.invoke(null, fallbackPath, classLoader)
            patchedPathAndClassLoader as? Pair<*, *>
        }?.first as? String

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
