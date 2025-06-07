package org.jetbrains.jewel.bridge

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.icons.patchIconPath
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ui.painter.PainterPathHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.ResourcePainterProviderScope

/**
 * A [PainterPathHint] that implements the
 * [New UI Icon Mapping](https://plugins.jetbrains.com/docs/intellij/icons.html#mapping-entries) by delegating to the
 * IntelliJ Platform.
 */
internal object BridgeOverride : PainterPathHint {
    private val dirProvider = DirProvider()

    @Suppress("UnstableApiUsage") // patchIconPath() is explicitly open to us
    override fun PainterProviderScope.patch(): String {
        if (this !is ResourcePainterProviderScope) return path

        // For all provided classloaders, we try to get the patched path, both using
        // the original path, and an "abridged" path that has gotten the icon path prefix
        // removed (the classloader is set up differently in prod IDEs and when running
        // from Gradle, and the icon could be in either place depending on the environment)
        val fallbackPath = path.removePrefix(dirProvider.dir())

        for (classLoader in classLoaders) {
            val patchedPath =
                patchIconPath(path.removePrefix("/"), classLoader)?.first
                    ?: patchIconPath(fallbackPath, classLoader)?.first

            // 233 EAP 4 broke path patching horribly; now it can return a
            // "reflective path", which is a FQN to an ExpUIIcons entry.
            // As a (hopefully) temporary solution, we undo this transformation
            // back into the original path. The initial transform is lossy, and
            // this attempt might fail.
            if (patchedPath?.startsWith("com.intellij.icons.ExpUiIcons") == true) {
                return inferActualPathFromReflectivePath(patchedPath)
            }

            if (patchedPath != null) {
                return patchedPath
            }
        }
        return path
    }

    private fun inferActualPathFromReflectivePath(patchedPath: String): String {
        val iconPath = patchedPath.removePrefix("com.intellij.icons.ExpUiIcons.")

        return buildString {
            append("expui/")
            iconPath
                .split('.')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach {
                    append(it.first().lowercaseChar())
                    append(it.drop(1))
                    append('/')
                }
            replace(length - 1, length, "") // Drop last '/'
            if (iconPath.contains("_dark")) append("_dark")
            append(".svg")

            Logger.getInstance("IconsPathPatching")
                .warn(
                    "IntelliJ returned a reflective path: $patchedPath for $iconPath." +
                        " We reverted that to a plausible-looking resource path: ${toString()}"
                )
        }
    }

    override fun toString(): String = "BridgeOverride"
}
