package org.jetbrains.jewel.foundation.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.jewel.foundation.code.MimeType

public object NoOpCodeHighlighter : CodeHighlighter {
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> = flowOf(AnnotatedString(code))
}
