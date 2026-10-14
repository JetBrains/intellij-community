// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
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
     * Returns the language for the code fence with the given [infoString], or `null` when this resolver does not know
     * the language.
     *
     * @param infoString The info string of the code fence. It is not trimmed and not lowercased.
     */
    public fun resolve(infoString: String): CodeFenceLanguage?

    public companion object {
        internal val EP_NAME: ExtensionPointName<CodeFenceLanguageResolver> =
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
public class CodeFenceLanguage(public val language: Language, public val extension: String? = null)
