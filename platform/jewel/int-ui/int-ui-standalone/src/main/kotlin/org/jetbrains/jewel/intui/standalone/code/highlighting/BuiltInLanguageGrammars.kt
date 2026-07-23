// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.JAVA
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.KOTLIN

// Patterns are adapted from the tmLanguage grammars in plugins/textmate/lib/bundles/.
// Java's regex engine is used (see TokenRule for known PCRE/Oniguruma incompatibilities).
internal object BuiltInLanguageGrammars {
    val all: List<LanguageGrammar> by lazy { listOf(KOTLIN, JAVA) }
}
