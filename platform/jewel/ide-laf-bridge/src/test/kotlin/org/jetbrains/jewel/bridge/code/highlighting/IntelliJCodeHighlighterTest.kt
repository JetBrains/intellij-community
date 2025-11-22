// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.graphics.Color
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.ui.UIUtil
import java.io.File
import kotlin.io.path.pathString
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.TextMateServiceImpl
import org.jetbrains.plugins.textmate.configuration.TextMatePersistentBundle
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings
import org.junit.Test

private val languages = setOf("kotlin")

@Suppress("RAW_RUN_BLOCKING")
internal class IntelliJCodeHighlighterTest : LightPlatform4TestCase() {
    private lateinit var highlighter: IntelliJCodeHighlighter

    override fun setUp() {
        super.setUp()

        val communityHome = PathManager.getCommunityHomePath()
        val baseTextMatePluginPath =
            File(communityHome, "plugins/textmate-plugin").takeIf { it.exists() }
                ?: File(communityHome, "plugins/textmate")

        // TextMate implementation was based on 'TextMateAcceptanceTestCase'
        // However, we need to first check if we are running on Gradle or IntelliJ.
        // The plugin folder on Gradle is 'textmate-plugin', however, on IntelliJ it's just 'textmate'.
        val textMateService = TextMateService.getInstance() as TextMateServiceImpl
        val settings = TextMateUserBundlesSettings.getInstance()!!

        if (settings.bundles.values.map { it.name }.toSet() != languages) {
            settings.setBundlesConfig(
                languages.associate {
                    baseTextMatePluginPath.toPath().resolve("lib/bundles/$it").pathString to
                        TextMatePersistentBundle(it, enabled = true)
                }
            )
            textMateService.disableBuiltinBundles(getTestRootDisposable())
            UIUtil.dispatchAllInvocationEvents()
        }

        highlighter = IntelliJCodeHighlighter(project, flowOf(Unit))
    }

    override fun tearDown() {
        try {
            TextMateUserBundlesSettings.getInstance()!!.setBundlesConfig(emptyMap())
            UIUtil.dispatchAllInvocationEvents()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `should highlight html code using native highlighter`() {
        val code = "<p>Hello, Text!</p>"
        val languageName = "HTML"

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("HTML code should have been highlighted", annotatedString.spanStyles.isNotEmpty())
        assertFalse(
            "At least one token should have a color",
            annotatedString.spanStyles.all { it.item.color == Color.Unspecified },
        )
    }

    @Test
    fun `should highlight unsupported language using TextMate fallback`() {
        val code =
            """
            |object Hello {
            |  val world = "Hello World"
            |}
            """
                .trimMargin()
        val languageName = "kotlin" // Assuming the kotlin plugin isn't installed, so it can fall back to TextMate

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("Kotlin code should be highlighted by TextMate", annotatedString.spanStyles.size > 1)
    }

    @Test
    fun `should return plain text for unknown language`() {
        val code = "some random text"
        val languageName = "not-a-real-language"

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("Unknown language should not be highlighted", annotatedString.spanStyles.isEmpty())
    }

    @Test
    fun `should return plain text for blank language name`() {
        val code = "public class Test {}"

        val annotatedString = runBlocking { highlighter.highlight(code, "").last() }

        assertEquals(code, annotatedString.text)
        assertTrue(annotatedString.spanStyles.isEmpty())
    }
}
