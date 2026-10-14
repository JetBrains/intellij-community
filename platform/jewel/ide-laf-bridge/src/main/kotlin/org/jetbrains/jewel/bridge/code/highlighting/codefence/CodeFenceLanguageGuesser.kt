// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil

/**
 * Guesses the [Language] of a Markdown code fence from its info string.
 *
 * The guesser tries these sources in order, and returns the first injectable language:
 * 1. The registered [CodeFenceLanguageResolver] extensions. The guesser gives each resolver the info string without
 *    changes.
 * 2. The alias table of the guesser. The guesser lowercases and trims the info string, and maps an alias, such as `kt`,
 *    to a language ID. When no registered language has that ID, it removes the last word and tries again. For example,
 *    `kotlin title=demo` gives the Kotlin language.
 *
 * The guesser returns `null` when no source gives an injectable language.
 */
internal object CodeFenceLanguageGuesser {
    @JvmStatic
    fun guessLanguageForInjection(value: String): CodeFenceLanguage? {
        val resolved = resolveWithExtensions(value)
        if (resolved != null) return resolved

        val language = findLanguage(value.lowercase())?.takeIf { LanguageUtil.isInjectableLanguage(it) } ?: return null
        return CodeFenceLanguage(language)
    }

    /**
     * Returns the first injectable language that a registered [CodeFenceLanguageResolver] gives for [value].
     *
     * The bridge module descriptor declares the extension point. A test application can run without this descriptor.
     * Then the extension point is not registered, and this function returns `null`.
     */
    private fun resolveWithExtensions(value: String): CodeFenceLanguage? =
        CodeFenceLanguageResolver.EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { resolver ->
            resolver.resolve(value)?.takeIf { LanguageUtil.isInjectableLanguage(it.language) }
        }

    private fun findLanguage(value: String, registeredLanguages: Collection<Language>): Language? {
        val entry = CodeFenceLanguageAliases.findRegisteredEntry(value) ?: value
        val registered = registeredLanguages.find { it.id.equals(entry, ignoreCase = true) }
        if (registered != null) {
            return registered
        }
        return null
    }

    private fun findLanguage(value: String): Language? {
        val registeredLanguages = Language.getRegisteredLanguages()
        var name = value.trim()
        while (true) {
            val language = findLanguage(name, registeredLanguages)
            if (language != null) {
                return language
            }
            val index = name.indexOfLast { it.isWhitespace() }
            if (index == -1) {
                return null
            }
            name = name.substring(0, index).trimEnd()
        }
    }
}
