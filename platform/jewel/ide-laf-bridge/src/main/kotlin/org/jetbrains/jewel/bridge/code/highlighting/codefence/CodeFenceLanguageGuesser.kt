// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil

/**
 * Guesses the [Language] of a Markdown code fence from its info string.
 *
 * The guesser asks the registered [CodeFenceLanguageResolver] extensions first. The Markdown plugin registers one that
 * reads the `org.intellij.markdown.fenceLanguageProvider` extension point. The default Markdown provider in that
 * extension point reads the `org.intellij.markdown.additionalFenceLanguageSuggester` extension point.
 *
 * When no resolver gives an injectable language, the guesser uses its own alias table. The fallback is copied from
 * `org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser`.
 */
internal object CodeFenceLanguageGuesser {
    @JvmStatic
    fun guessLanguageForInjection(value: String): CodeFenceLanguage? {
        val resolved = resolveWithExtensions(value)
        if (resolved != null) {
            return resolved
        }
        val language = findLanguage(value.lowercase())?.takeIf { LanguageUtil.isInjectableLanguage(it) } ?: return null
        return CodeFenceLanguage(language)
    }

    // The extension point is missing when the bridge module descriptor is not loaded, for example in some tests.
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
        val exactMatch = findLanguage(value, registeredLanguages)
        if (exactMatch != null) {
            return exactMatch
        }
        var index = value.lastIndexOf(' ')
        while (index != -1) {
            val nameWithoutCustomizations = value.take(index)
            val language = findLanguage(nameWithoutCustomizations, registeredLanguages)
            if (language != null) {
                return language
            }
            index = value.lastIndexOf(' ', startIndex = (index - 1).coerceAtLeast(0))
        }
        return null
    }
}
