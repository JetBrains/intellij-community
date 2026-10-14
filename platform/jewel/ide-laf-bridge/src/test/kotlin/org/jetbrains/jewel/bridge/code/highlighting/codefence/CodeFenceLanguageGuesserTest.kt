// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test

internal class CodeFenceLanguageGuesserTest : LightPlatform4TestCase() {
    private val html: Language
        get() = checkNotNull(Language.findLanguageByID("HTML"))

    private val xml: Language
        get() = checkNotNull(Language.findLanguageByID("XML"))

    private val regExp: Language
        get() = checkNotNull(Language.findLanguageByID("RegExp"))

    override fun setUp() {
        super.setUp()

        // The test application does not load the bridge module descriptor, which declares the extension point.
        val area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
        if (!area.hasExtensionPoint(CodeFenceLanguageResolver.EP_NAME)) {
            area.registerExtensionPoint(
                CodeFenceLanguageResolver.EP_NAME,
                CodeFenceLanguageResolver::class.java.name,
                ExtensionPoint.Kind.INTERFACE,
                testRootDisposable,
            )
        }
    }

    @Test
    fun `should use the language from a resolver`() {
        maskResolvers(resolverFor("my-html", CodeFenceLanguage(html, "htm")))

        val guessed = CodeFenceLanguageGuesser.guessLanguageForInjection("my-html")

        assertEquals(html, guessed?.language)
        assertEquals("htm", guessed?.extension)
    }

    @Test
    fun `should prefer a resolver to the alias table`() {
        maskResolvers(resolverFor("html", CodeFenceLanguage(xml, "xsd")))

        val guessed = CodeFenceLanguageGuesser.guessLanguageForInjection("html")

        assertEquals(xml, guessed?.language)
        assertEquals("xsd", guessed?.extension)
    }

    @Test
    fun `should pass the raw info string to a resolver`() {
        val received = mutableListOf<String>()
        maskResolvers(
            object : CodeFenceLanguageResolver {
                override fun resolve(infoString: String): CodeFenceLanguage? {
                    received += infoString
                    return null
                }
            }
        )

        CodeFenceLanguageGuesser.guessLanguageForInjection("My-HTML title=x")

        assertEquals(listOf("My-HTML title=x"), received)
    }

    @Test
    fun `should skip a resolver that gives a language that is not injectable`() {
        maskResolvers(
            resolverFor("my-html", CodeFenceLanguage(Language.ANY)),
            resolverFor("my-html", CodeFenceLanguage(html)),
        )

        assertEquals(html, CodeFenceLanguageGuesser.guessLanguageForInjection("my-html")?.language)
    }

    @Test
    fun `should fallback to the alias table when all resolvers give languages that are not injectable`() {
        maskResolvers(
            resolverFor("html", CodeFenceLanguage(Language.ANY, "htm")),
            resolverFor("html", CodeFenceLanguage(Language.ANY)),
        )

        val guessed = CodeFenceLanguageGuesser.guessLanguageForInjection("html")

        assertEquals(html, guessed?.language)
        assertNull("The alias table must give no extension for the info string 'html'", guessed?.extension)
    }

    @Test
    fun `should fall back to the alias table when no resolver gives a language`() {
        maskResolvers(resolverFor("other", CodeFenceLanguage(html)))

        val guessed = CodeFenceLanguageGuesser.guessLanguageForInjection("HTML")

        assertEquals(html, guessed?.language)
        assertNull("The alias table must give no extension for the info string 'HTML'", guessed?.extension)
    }

    @Test
    fun `should return null when neither a resolver nor the alias table knows the language`() {
        maskResolvers()

        assertNull(
            "The guesser must return null when no resolver and no alias knows the info string 'not-a-real-language'",
            CodeFenceLanguageGuesser.guessLanguageForInjection("not-a-real-language"),
        )
    }

    @Test
    fun `should return null for an unknown info string with a leading space`() {
        maskResolvers()

        assertNull(
            "The guesser must return null for the info string ' not-a-real-language'",
            CodeFenceLanguageGuesser.guessLanguageForInjection(" not-a-real-language"),
        )
    }

    @Test
    fun `should ignore a leading space in the info string`() {
        maskResolvers()

        assertEquals(html, CodeFenceLanguageGuesser.guessLanguageForInjection(" html")?.language)
    }

    @Test
    fun `should remove the words after a tab in the info string`() {
        maskResolvers()

        assertEquals(html, CodeFenceLanguageGuesser.guessLanguageForInjection("html\ttitle=x")?.language)
    }

    @Test
    fun `should return null for an empty or blank info string`() {
        maskResolvers()

        for (infoString in listOf("", " ", "   ", "\t", " \t ")) {
            assertNull(
                "The guesser must return null for the info string '$infoString'",
                CodeFenceLanguageGuesser.guessLanguageForInjection(infoString),
            )
        }
    }

    @Test
    fun `should ignore a trailing space in the info string`() {
        maskResolvers()

        assertEquals(html, CodeFenceLanguageGuesser.guessLanguageForInjection("html ")?.language)
    }

    @Test
    fun `should remove several words and repeated whitespace after the language`() {
        maskResolvers()

        assertEquals(html, CodeFenceLanguageGuesser.guessLanguageForInjection("html  title=x \t lines=1-3  ")?.language)
    }

    @Test
    fun `should map an alias to the language ID`() {
        maskResolvers()

        assertEquals(regExp, CodeFenceLanguageGuesser.guessLanguageForInjection("regex")?.language)
    }

    @Test
    fun `should map an alias with other case and words after it`() {
        maskResolvers()

        assertEquals(regExp, CodeFenceLanguageGuesser.guessLanguageForInjection("Regex title=x")?.language)
    }

    private fun maskResolvers(vararg resolvers: CodeFenceLanguageResolver) {
        ExtensionTestUtil.maskExtensions(CodeFenceLanguageResolver.EP_NAME, resolvers.toList(), testRootDisposable)
    }

    private fun resolverFor(knownInfoString: String, result: CodeFenceLanguage) =
        object : CodeFenceLanguageResolver {
            override fun resolve(infoString: String): CodeFenceLanguage? =
                result.takeIf { infoString == knownInfoString }
        }
}
