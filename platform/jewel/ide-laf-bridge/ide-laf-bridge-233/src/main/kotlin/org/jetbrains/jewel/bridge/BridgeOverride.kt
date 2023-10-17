package org.jetbrains.jewel.bridge

import com.intellij.ui.icons.patchIconPath
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.painter.PainterResourcePathHint

internal object BridgeOverride : PainterResourcePathHint {

    private val dirProvider = DirProvider()

    override fun patch(path: String, classLoaders: List<ClassLoader>): String {
        // For all provided classloaders, we try to get the patched path, both using
        // the original path, and an "abridged" path that has gotten the icon path prefix
        // removed (the classloader is set up differently in prod IDEs and when running
        // from Gradle, and the icon could be in either place depending on the environment)
        val fallbackPath = path.removePrefix(dirProvider.dir())

        for (classLoader in classLoaders) {
            val patchedPath = patchIconPath(path.removePrefix("/"), classLoader)?.first
                ?: patchIconPath(fallbackPath, classLoader)?.first

            if (patchedPath != null) {
                return patchedPath
            }
        }
        return path
    }

    override fun toString(): String = "BridgeOverride"
}
