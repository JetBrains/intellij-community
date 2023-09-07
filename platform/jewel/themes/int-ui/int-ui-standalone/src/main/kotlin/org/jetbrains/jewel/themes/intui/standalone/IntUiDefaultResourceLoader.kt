package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.JewelResourceLoader
import java.io.InputStream

object IntUiDefaultResourceLoader : ResourceLoader, JewelResourceLoader {

    override val searchClasses = listOf(IntUiTheme::class.java, IntUiDefaultResourceLoader::class.java)

    override fun load(resourcePath: String): InputStream {
        val resource = loadOrNull(resourcePath)
        return requireNotNull(resource) { "Resource $resourcePath not found" }
    }

    fun loadOrNull(resourcePath: String): InputStream? {
        val normalizedPath = if (!resourcePath.startsWith("/")) "/$resourcePath" else resourcePath
        return searchClasses.firstNotNullOfOrNull { it.getResourceAsStream(normalizedPath) }
    }
}
