package org.jetbrains.jewel.foundation.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType

@ApiStatus.Experimental
@ExperimentalJewelApi
public object NoOpCodeHighlighter : CodeHighlighter {
    @Deprecated(
        message =
            "This method is not scalable as it relies on a pre-resolved MimeType object. " +
                "This prevents automatic support for languages not explicitly defined in the MimeType system" +
                "(e.g., from TextMate bundles). Use the overload that accepts the raw " +
                "`language` string instead.",
        replaceWith = ReplaceWith("highlight(code, language)"),
    )
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> = flowOf(AnnotatedString(code))

    override fun highlight(code: String, language: String): Flow<AnnotatedString> = flowOf(AnnotatedString(code))
}
