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
    @Deprecated(
        message =
            "This method is not scalable as it relies on a pre-resolved MimeType object. " +
                "This prevents automatic support for languages not explicitly defined in the MimeType system" +
                "(e.g., from TextMate bundles). Use the overload that accepts the raw " +
                "`language` string instead.",
        replaceWith = ReplaceWith("highlight(code, language)"),
    )
    public fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString>

    /**
     * Highlights the given `code` string based on the provided `language`.
     *
     * This function uses available syntax definitions to apply styling to the code. The highlighting is dynamic,
     * meaning it can update automatically if the environment's styling, such as the active color scheme, changes. For
     * static highlighting, simply use the first value emitted from the returned flow.
     *
     * If the `language` is blank, or doesn't match any known language, the function will return the original code as a
     * plain, un-styled `AnnotatedString`.
     *
     * @param code The source code to highlight.
     * @param language A string that identifies the programming language, typically a file extension or the language
     *   name (e.g., "kt", "py", "js", "ruby").
     * @return A [Flow] that emits an `AnnotatedString` with syntax highlighting applied. The flow will emit new values
     *   in response to theme or color scheme changes.
     */
    public fun highlight(code: String, language: String = ""): Flow<AnnotatedString>
}

@ExperimentalJewelApi
@get:ApiStatus.Experimental
public val LocalCodeHighlighter: ProvidableCompositionLocal<CodeHighlighter> = staticCompositionLocalOf {
    NoOpCodeHighlighter
}
