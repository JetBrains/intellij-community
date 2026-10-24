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
    fun `should fall back to the alias table when no resolver gives a language`() {
        maskResolvers(resolverFor("other", CodeFenceLanguage(html)))

        val guessed = CodeFenceLanguageGuesser.guessLanguageForInjection("HTML")

        assertEquals(html, guessed?.language)
        assertNull(guessed?.extension)
    }

    @Test
    fun `should return null when neither a resolver nor the alias table knows the language`() {
        maskResolvers()

        assertNull(CodeFenceLanguageGuesser.guessLanguageForInjection("not-a-real-language"))
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
