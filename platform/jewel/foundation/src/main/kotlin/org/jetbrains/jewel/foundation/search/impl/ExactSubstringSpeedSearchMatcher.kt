// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search.impl

import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.MatchResult

/**
 * Tells whether a string matches a specific substring. Allows for lowercase substring matching.
 *
 * **Swing equivalent:**
 * [MinusculeMatcherImpl.matchBySubstring](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcherImpl.java)
 */
internal class ExactSubstringSpeedSearchMatcher(private val pattern: String, private val ignoreCase: Boolean = true) :
    SpeedSearchMatcher {
    override fun matches(text: String?): MatchResult = matches(text as? CharSequence)

    override fun matches(text: CharSequence?): MatchResult {
        if (pattern.isBlank() || text.isNullOrBlank()) return MatchResult.NoMatch
        val matchIndex = text.indexOf(pattern, ignoreCase = ignoreCase)
        return if (matchIndex >= 0) {
            MatchResult.Match(listOf(matchIndex until (matchIndex + pattern.length)))
        } else {
            MatchResult.NoMatch
        }
    }
}
