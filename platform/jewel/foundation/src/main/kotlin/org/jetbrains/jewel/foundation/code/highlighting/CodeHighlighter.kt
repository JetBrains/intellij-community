package org.jetbrains.jewel.foundation.code.highlighting

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface CodeHighlighter {
    /**
     * Highlights [code] according to rules for the language specified by [mimeType], and returns flow of styled
     * strings. For basic highlighters with rigid color schemes it is enough to return a flow of one element:
     * ```
     * return flowOf(highlightedString(code, mimeType))
     * ```
     *
     * However, some implementations might want gradual highlighting (for example, apply something simple while waiting
     * for the extensive info from server), or they might rely upon a color scheme that can change at any time.
     *
     * In such cases, they need to produce more than one styled string for the same piece of code, and that's when flows
     * come in handy.
     *
     * @see [NoOpCodeHighlighter]
     */
    public fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString>
}

public val LocalCodeHighlighter: ProvidableCompositionLocal<CodeHighlighter> = staticCompositionLocalOf {
    NoOpCodeHighlighter
}
