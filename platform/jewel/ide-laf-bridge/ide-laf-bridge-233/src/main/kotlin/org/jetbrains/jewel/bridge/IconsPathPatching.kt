package org.jetbrains.jewel.bridge

import com.intellij.ui.icons.patchIconPath
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.InternalJewelApi

@InternalJewelApi
@Suppress("UnstableApiUsage")
fun getPatchedIconPath(
    dirProvider: DirProvider,
    originalPath: String,
    classLoaders: List<ClassLoader>,
): String? {
    // For all provided classloaders, we try to get the patched path, both using
    // the original path, and an "abridged" path that has gotten the icon path prefix
    // removed (the classloader is set up differently in prod IDEs and when running
    // from Gradle, and the icon could be in either place depending on the environment)
    val fallbackPath = originalPath.removePrefix(dirProvider.dir())

    for (classLoader in classLoaders) {
        val patchedPath = patchIconPath(originalPath.removePrefix("/"), classLoader)?.first
            ?: patchIconPath(fallbackPath, classLoader)?.first

        if (patchedPath != null) {
            return patchedPath
        }
    }
    return null
}
