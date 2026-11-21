// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Resolves the [Language] of a Markdown code fence from its info string.
 *
 * [CodeFenceLanguageGuesser] asks the registered resolvers first. It uses its own alias table only when no resolver
 * gives a result. The Markdown plugin registers a resolver that forwards to its own code fence extension points.
 */
@ApiStatus.Internal
@InternalJewelApi
public interface CodeFenceLanguageResolver {
    /**
     * Returns the language for a Markdown code fence, or `null` when this resolver does not know the language.
     *
     * @param infoString The info string of the code fence: the text after the opening backticks or tildes, on the same
     *   line. For example, `kotlin` or `kotlin title=demo`. [CodeFenceLanguageGuesser] gives it without changes, so it
     *   can have other case and surrounding whitespace.
     */
    public fun resolve(infoString: String): CodeFenceLanguage?

    public companion object {
        public val EP_NAME: ExtensionPointName<CodeFenceLanguageResolver> =
            ExtensionPointName.create("org.jetbrains.jewel.bridge.codeFenceLanguageResolver")
    }
}

/**
 * The result of a [CodeFenceLanguageResolver].
 *
 * @property language The language to highlight the code fence with.
 * @property extension The file extension that refines the [language], or `null`. For example, the resolver can return
 *   the TextMate language with the `js` extension.
 */
@ApiStatus.Internal
@InternalJewelApi
@GenerateDataFunctions
public class CodeFenceLanguage(public val language: Language, public val extension: String? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CodeFenceLanguage

        if (language != other.language) return false
        if (extension != other.extension) return false

        return true
    }

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }

    override fun toString(): String = "CodeFenceLanguage(language=$language, extension=$extension)"
}
