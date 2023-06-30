package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.ui.res.ResourceLoader
import java.io.InputStream

object IntUiDefaultResourceLoader : ResourceLoader {

    override fun load(resourcePath: String): InputStream {
        val normalizedPath = if (!resourcePath.startsWith("/")) "/$resourcePath" else resourcePath
        val resource = IntUiTheme::class.java.getResourceAsStream(normalizedPath)
            ?: IntUiDefaultResourceLoader::class.java.getResourceAsStream(normalizedPath)
        return requireNotNull(resource) { "Resource $resourcePath not found" }
    }
}
