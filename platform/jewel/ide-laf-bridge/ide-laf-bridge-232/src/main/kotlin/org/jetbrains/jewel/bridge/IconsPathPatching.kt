package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.InternalJewelApi

@InternalJewelApi
fun getPatchedIconPath(
    dirProvider: DirProvider,
    originalPath: String,
    classLoaders: List<ClassLoader>,
): String? {
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

    return patchedPath
}
