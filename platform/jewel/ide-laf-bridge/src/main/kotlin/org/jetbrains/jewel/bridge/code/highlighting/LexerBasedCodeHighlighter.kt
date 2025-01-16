package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import java.awt.Font
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter

internal class LexerBasedCodeHighlighter(
    private val project: Project,
    private val reHighlightingRequests: Flow<Unit>,
    private val highlightDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CodeHighlighter {
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> {
        val language = mimeType?.toLanguageOrNull() ?: return flowOf(AnnotatedString(code))
        val fileExtension = language.associatedFileType?.defaultExtension ?: return flowOf(AnnotatedString(code))
        val virtualFile = LightVirtualFile("markdown_code_block_${code.hashCode()}.$fileExtension", language, code)
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        val highlighter =
            SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, virtualFile)
                ?: return flowOf(AnnotatedString(code))

        return flow {
            highlightAndEmit(highlighter, code, colorScheme)
            reHighlightingRequests.collect { highlightAndEmit(highlighter, code, colorScheme) }
        }
    }

    private suspend fun FlowCollector<AnnotatedString>.highlightAndEmit(
        highlighter: SyntaxHighlighter,
        code: String,
        colorScheme: EditorColorsScheme,
    ) {
        emit(withContext(highlightDispatcher) { doHighlight(highlighter, code, colorScheme) })
    }

    private fun doHighlight(
        highlighter: SyntaxHighlighter,
        code: String,
        colorScheme: EditorColorsScheme,
    ): AnnotatedString = buildAnnotatedString {
        with(highlighter.highlightingLexer) {
            start(code)

            while (tokenType != null) {
                val attributes: TextAttributes? = run {
                    val attrKey = highlighter.getTokenHighlights(tokenType).lastOrNull() ?: return@run null
                    colorScheme.getAttributes(attrKey) ?: attrKey.defaultAttributes
                }
                withTextAttributes(attributes) { append(tokenText) }
                advance()
            }
        }
    }

    private fun MimeType.toLanguageOrNull(): Language? = LanguageUtil.findRegisteredLanguage(displayName().lowercase())

    private fun AnnotatedString.Builder.withTextAttributes(
        textAttributes: TextAttributes?,
        block: AnnotatedString.Builder.() -> Unit,
    ) {
        if (textAttributes == null) {
            return block()
        }
        withStyle(textAttributes.toSpanStyle(), block)
    }

    private fun TextAttributes.toSpanStyle() =
        SpanStyle(
            color = foregroundColor.toComposeColorOrUnspecified(),
            fontWeight = if (fontType and Font.BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (fontType and Font.ITALIC != 0) FontStyle.Italic else null,
            background = backgroundColor.toComposeColorOrUnspecified(),
            textDecoration =
                when (effectType) {
                    EffectType.LINE_UNDERSCORE -> TextDecoration.Underline
                    EffectType.STRIKEOUT -> TextDecoration.LineThrough
                    else -> null
                },
        )
}
