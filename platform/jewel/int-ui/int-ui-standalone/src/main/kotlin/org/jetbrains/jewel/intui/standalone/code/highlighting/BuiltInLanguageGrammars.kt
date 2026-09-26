// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.C
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.CSS
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.HTML
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.JAVA
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.JAVASCRIPT
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.JSON
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.JSX
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.KOTLIN
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.PYTHON
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.SHELL
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.SQL
import org.jetbrains.jewel.intui.standalone.code.highlighting.languages.YAML

// Patterns are adapted from the tmLanguage grammars in plugins/textmate/lib/bundles/.
// Java's regex engine is used (see TokenRule for known PCRE/Oniguruma incompatibilities).
@ApiStatus.Internal
@InternalJewelApi
public object BuiltInLanguageGrammars {
    public val all: List<LanguageGrammar> by lazy {
        listOf(KOTLIN, JAVA, JSON, JAVASCRIPT, JSX, PYTHON, YAML, HTML, CSS, SQL, SHELL, C)
    }
}
