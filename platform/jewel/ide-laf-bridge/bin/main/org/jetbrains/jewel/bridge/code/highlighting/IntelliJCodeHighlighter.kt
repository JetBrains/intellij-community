// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lexer.EmptyLexer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.code.highlighting.codefence.CodeFenceLanguageAliases
import org.jetbrains.jewel.bridge.code.highlighting.codefence.CodeFenceLanguageGuesser
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter

/**
 * The primary implementation of the [CodeHighlighter] interface for the IntelliJ Platform.
 *
 * This class leverages the IDE's built-in services, such as native language plugins and TextMate bundle support, to
 * provide rich, theme-aware syntax highlighting.
 *
 * The highlighting results are provided as a [Flow] to allow for dynamic updates when the IDE's color scheme changes.
 *
 * @param project The active IntelliJ [Project].
 * @param reHighlightingRequests A [Flow] that emits a value when a re-highlighting pass is needed.
 * @param highlightDispatcher The [CoroutineDispatcher] on which the highlighting work should be performed.
 */
internal class IntelliJCodeHighlighter(
    private val project: Project,
    private val reHighlightingRequests: Flow<Unit>,
    private val highlightDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CodeHighlighter {
    private val codeAnnotator = CodeAnnotator()
    private val colorScheme: EditorColorsScheme
        get() = EditorColorsManager.getInstance().globalScheme

    @Deprecated(
        "This method is not scalable as it relies on a pre-resolved MimeType object." +
            "This prevents automatic support for languages not explicitly defined in the MimeType" +
            "system(e.g., from TextMate bundles)." +
            "Use the overload that accepts the raw `language` string instead.",
        replaceWith = ReplaceWith("highlight(code, language)"),
    )
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> {
        val language = mimeType?.toLanguageOrNull() ?: return flowOf(AnnotatedString(code))
        val fileExtension = language.associatedFileType?.defaultExtension ?: return flowOf(AnnotatedString(code))
        val virtualFile = LightVirtualFile("markdown_code_block_${code.hashCode()}.$fileExtension", language, code)
        val highlighter =
            SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, virtualFile)
                ?: return flowOf(AnnotatedString(code))

        return flow {
            highlightAndEmit(highlighter, code, colorScheme)
            reHighlightingRequests.collect { highlightAndEmit(highlighter, code, colorScheme) }
        }
    }

    /**
     * Highlights the given `code` string based on the provided `language`.
     *
     * This function is highly dynamic. It first attempts to find a native IntelliJ Platform plugin that supports the
     * given language extension. If no native plugin is found, it gracefully falls back to using any enabled TextMate
     * bundles that match the extension. This allows it to automatically support a wide range of languages without prior
     * configuration.
     *
     * The result is returned as a [Flow] to support dynamic updates, such as when the user changes the active IDE color
     * scheme. For simple, static highlighting, you can simply collect the first emitted value.
     *
     * If the `languageFileExtension` is null or does not correspond to any known language or active TextMate bundle,
     * the code will be emitted as a plain, un-styled `AnnotatedString`.
     *
     * @param code The source code to be highlighted.
     * @param language The file extension or Markdown info string identifying the programming language (e.g., "kt",
     *   "py", "js", "bat"). This is case-insensitive. Common aliases (like "js" for "javascript") are supported if
     *   there is a native IntelliJ Platform plugin installed that supports the language extension.
     * @return A [Flow] that emits an `AnnotatedString` with syntax highlighting applied. The flow may emit new values
     *   if the underlying color scheme changes.
     * @see [NoOpCodeHighlighter]
     */
    override fun highlight(code: String, language: String): Flow<AnnotatedString> {
        if (language.isBlank()) {
            return flowOf(AnnotatedString(code))
        }

        // First, try to find a highlighter using the standard IntelliJ Platform services.
        // This is the best-case scenario as it uses the full power of an installed language plugin.
        val nativeHighlighter = findNativeHighlighter(language)
        if (nativeHighlighter != null) {
            return createHighlightingFlow(nativeHighlighter, code)
        }

        // If no native highlighter is found, attempt to use the built-in TextMate bundle support as a fallback.
        val textMateHighlighter = findTextMateHighlighter(code, language)
        if (textMateHighlighter != null) {
            return createHighlightingFlow(textMateHighlighter, code)
        }

        return flowOf(AnnotatedString(code))
    }

    /**
     * Attempts to find a high-quality highlighter from a native IntelliJ Platform language plugin.
     *
     * @param name The language name or alias (e.g., "kotlin", "kt").
     * @return A [SyntaxHighlighter] if a native plugin is found, otherwise `null`.
     */
    private fun findNativeHighlighter(name: String): SyntaxHighlighter? {
        val language = CodeFenceLanguageGuesser.guessLanguageForInjection(name) ?: return null

        return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, null)?.takeIf {
            it.highlightingLexer !is EmptyLexer
        }
    }

    /**
     * Attempts to find a fallback highlighter from the IntelliJ Platform's TextMate bundle support.
     *
     * This is used when no native language plugin is available for the given language name.
     *
     * @param code The source code to highlight, needed for the [LightVirtualFile].
     * @param name The language name, which will be used as the file extension to find a matching TextMate bundle.
     * @return A [SyntaxHighlighter] if a matching and enabled TextMate bundle is found, otherwise `null`.
     */
    private fun findTextMateHighlighter(code: String, name: String): SyntaxHighlighter? {
        val textMateLanguage = Language.findLanguageByID("textmate") ?: return null
        val extension = CodeFenceLanguageAliases.findExtensionGivenAlias(name)

        val virtualFile = LightVirtualFile("markdown_code_block_${code.hashCode()}.$extension", textMateLanguage, code)

        return SyntaxHighlighterFactory.getSyntaxHighlighter(textMateLanguage, project, virtualFile)?.takeIf {
            it.highlightingLexer !is EmptyLexer
        }
    }

    /**
     * Creates the [Flow] that performs the initial highlighting and listens for re-highlighting requests.
     *
     * @param highlighter The [SyntaxHighlighter] to use for annotating the code.
     * @param code The source code to highlight.
     * @return A [Flow] that emits styled strings.
     */
    private fun createHighlightingFlow(highlighter: SyntaxHighlighter, code: String): Flow<AnnotatedString> {
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        return flow {
            highlightAndEmit(highlighter, code, colorScheme)
            reHighlightingRequests.collect { highlightAndEmit(highlighter, code, colorScheme) }
        }
    }

    /**
     * Performs the highlighting on the [highlightDispatcher], annotates the code, and emits it to the flow.
     *
     * @param highlighter The configured [SyntaxHighlighter].
     * @param code The source code to highlight.
     * @param colorScheme The active IDE color scheme.
     */
    private suspend fun FlowCollector<AnnotatedString>.highlightAndEmit(
        highlighter: SyntaxHighlighter,
        code: String,
        colorScheme: EditorColorsScheme,
    ) {
        emit(withContext(highlightDispatcher) { codeAnnotator.annotate(code, highlighter, colorScheme) })
    }

    // Convenience helper for the deprecated [highlight] method.
    private fun MimeType.toLanguageOrNull(): Language? = LanguageUtil.findRegisteredLanguage(displayName().lowercase())
}
