package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.painter.PainterResourcePathHint

internal object BridgeOverride : PainterResourcePathHint {

    private val dirProvider = DirProvider()

    private val patchIconPath by lazy {
        val clazz = Class.forName("com.intellij.ui.icons.CachedImageIconKt")
        val patchIconPath = clazz.getMethod("patchIconPath", String::class.java, ClassLoader::class.java)
        patchIconPath.isAccessible = true
        patchIconPath
    }

    override fun patch(path: String, classLoaders: List<ClassLoader>): String {
        // For all provided classloaders, we try to get the patched path, both using
        // the original path, and an "abridged" path that has gotten the icon path prefix
        // removed (the classloader is set up differently in prod IDEs and when running
        // from Gradle, and the icon could be in either place depending on the environment)
        val fallbackPath = path.removePrefix(dirProvider.dir())
        val patchedPath = classLoaders.firstNotNullOfOrNull { classLoader ->
            val patchedPathAndClassLoader =
                patchIconPath.invoke(null, path.removePrefix("/"), classLoader)
                    ?: patchIconPath.invoke(null, fallbackPath, classLoader)
            patchedPathAndClassLoader as? Pair<*, *>
        }?.first as? String

        return patchedPath ?: path
    }

    override fun toString(): String = "BridgeOverride"
}
