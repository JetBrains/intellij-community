// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class CodeFenceLanguageAliasesTest {
    @Test
    fun `should map an alias to the language ID`() {
        assertEquals("Kotlin", CodeFenceLanguageAliases.findRegisteredEntry("kt"))
    }

    @Test
    fun `should map the main alias to the language ID`() {
        assertEquals("Kotlin", CodeFenceLanguageAliases.findRegisteredEntry("kotlin"))
    }

    @Test
    fun `should ignore the case of an alias`() {
        assertEquals("Kotlin", CodeFenceLanguageAliases.findRegisteredEntry("KTS"))
    }

    @Test
    fun `should map an alias with a space`() {
        assertEquals("Shell Script", CodeFenceLanguageAliases.findRegisteredEntry("Shell Script"))
    }

    @Test
    fun `should return null for words after an alias`() {
        assertNull(
            "The guesser removes the words after an alias. The alias table must not match them.",
            CodeFenceLanguageAliases.findRegisteredEntry("kotlin title=x"),
        )
    }

    @Test
    fun `should return null for an unknown alias`() {
        assertNull(CodeFenceLanguageAliases.findRegisteredEntry("not-a-real-language"))
    }
}
