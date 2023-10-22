package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.Immutable
import org.w3c.dom.Element

/**
 * A [PainterHint] is a hint for [PainterProvider] on how to load a [Painter][androidx.compose.ui.graphics.painter.Painter].
 * It can used to patch the path of the resource being loaded (e.g., for New UI
 * icon path mapping, and handling the dark theme variants), replace colors in
 * an SVG based on the theme palette, etc.
 *
 * Custom implementations are not allowed. There are two types of hints:
 *  * [PainterPathHint] modifies the path of the resource to load
 *  * [PainterSvgPatchHint] modifies the contents of SVG resources
 *
 * @see PainterPathHint
 * @see PainterSvgPatchHint
 */
@Immutable
sealed interface PainterHint {

    fun canApplyTo(path: String): Boolean = true

    /**
     * An empty [PainterHint], it will be ignored.
     */
    companion object None : PainterHint {

        override fun canApplyTo(path: String): Boolean = false

        override fun toString(): String = "None"
    }
}

/**
 * Mark this [PainterHint] just available with SVG images.
 */
@Immutable
interface SvgPainterHint : PainterHint {

    override fun canApplyTo(path: String): Boolean = path.substringAfterLast('.').lowercase() == "svg"
}

/**
 * Mark this [PainterHint] just available with Bitmap images.
 */
@Immutable
interface BitmapPainterHint : PainterHint {

    override fun canApplyTo(path: String): Boolean = when (path.substringAfterLast('.').lowercase()) {
        "svg", "xml" -> false
        else -> true
    }
}

/**
 * Mark this [PainterHint] just available with XML images.
 */
@Immutable
interface XmlPainterHint : PainterHint {

    override fun canApplyTo(path: String): Boolean = path.substringAfterLast('.').lowercase() == "xml"
}

/**
 * A [PainterHint] that modifies the path of the resource being loaded.
 * Usage examples are applying the New UI icon mappings, or picking up
 * dark theme variants of icons.
 */
@Immutable
interface PainterPathHint : PainterHint {

    /**
     * Replace the entire path with the given value.
     */
    fun patch(path: String): String
}

/**
 * A [PainterHint] that modifies the module/jar path of the resource being loaded.
 * Main used in bridge mode.
 */
@Immutable
interface PainterResourcePathHint : PainterHint {

    /**
     * Patch the resource path with context classLoaders(for bridge module).
     */
    fun patch(path: String, classLoaders: List<ClassLoader>): String
}

/**
 * A [PainterHint] that patches the content of SVG resources. It is only applied
 * to SVG resources; it doesn't affect other types of resources.
 */
@Immutable
interface PainterSvgPatchHint : SvgPainterHint {

    /**
     * Patch the SVG content.
     */
    fun patch(element: Element)
}

/**
 * A [PainterHint] that adds a prefix to a resource file name, without
 * changing the rest of the path.
 * For example, if the original path is `icons/MyIcon.svg`, and the prefix
 * is `Dark`, the patched path will be `icons/DarkMyIcon.svg`.
 */
@Immutable
abstract class PainterPrefixHint : PainterPathHint {

    override fun patch(path: String): String = buildString {
        append(path.substringBeforeLast('/', ""))
        append('/')
        append(prefix())
        append(path.substringBeforeLast('.').substringAfterLast('/'))

        append('.')
        append(path.substringAfterLast('.'))
    }

    abstract fun prefix(): String
}

/**
 * A [PainterHint] that adds a suffix to a resource file name, without
 * changing the rest of the path nor the extension.
 * For example, if the original path is `icons/MyIcon.svg`, and the suffix
 * is `_dark`, the patched path will be `icons/MyIcon_dark.svg`.
 */
@Immutable
abstract class PainterSuffixHint : PainterPathHint {

    override fun patch(path: String): String = buildString {
        append(path.substringBeforeLast('/', ""))
        append('/')
        append(path.substringBeforeLast('.').substringAfterLast('/'))
        append(suffix())

        append('.')
        append(path.substringAfterLast('.'))
    }

    abstract fun suffix(): String
}
