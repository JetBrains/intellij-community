// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil

/**
 * Copied from org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
 *
 * For now, we removed support for both org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider and
 * org.intellij.plugins.markdown.injection.aliases.AdditionalFenceLanguageSuggester. In other words, we can't fetch
 * languages from custom providers.
 *
 * This will be solved in JEWEL-973.
 */
internal object CodeFenceLanguageGuesser {
    @JvmStatic
    fun guessLanguageForInjection(value: String): Language? =
        findLanguage(value.lowercase())?.takeIf { LanguageUtil.isInjectableLanguage(it) }

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
