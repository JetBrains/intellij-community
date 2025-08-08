package org.jetbrains.jewel.foundation.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

public object NoOpCodeHighlighter : CodeHighlighter {
    override fun highlight(code: String, langName: String?): Flow<AnnotatedString> = flowOf(AnnotatedString(code))
}
