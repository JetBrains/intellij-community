package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ClassLoaderProvider
import org.jetbrains.jewel.JewelResourceLoader
import java.io.InputStream

object BridgeResourceLoader : JewelResourceLoader(), ClassLoaderProvider {

    private val dirProvider = DirProvider()

    override val classLoaders
        get() = listOf(dirProvider::class.java.classLoader, javaClass.classLoader)

    override fun load(resourcePath: String): InputStream {
        val normalizedPath = resourcePath.removePrefix("/")
        val fallbackPath = resourcePath.removePrefix(dirProvider.dir())
        val resource = loadResourceOrNull(normalizedPath, classLoaders)
            ?: loadResourceOrNull(fallbackPath, classLoaders)

        return requireNotNull(resource) {
            "Resource '$resourcePath' not found (tried using '$normalizedPath' and '$fallbackPath')"
        }
    }
}
