// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
internal class DeclarativeHintsHighlightingResiliencyTest {
  companion object {
    private val projectFixture = projectFixture()
    private val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("Test.java", "class Test {}")
    private val psiFile by psiFileFixture
    private val factoryFixture = testFixture {
      val language = psiFileFixture.init().language
      val factory = extensionPointFixture(InlayHintsProviderFactory.EP) {
        TestInlayHintsProviderFactory(language)
      }.init()
      initialized(factory) {}
    }
    private val error = NoSuchMethodError("Broken provider")
  }

  private val editor by psiFileFixture.editorFixture()

  @Nested
  inner class ProviderFailure {
    @Suppress("unused")
    private val brokenProvider by factoryFixture.inlayHintProviderFixture("broken", collect = { _, _ -> throw error })

    @Suppress("unused")
    private val workingProvider by factoryFixture.inlayHintProviderFixture(
      "working",
      collect = { element, sink ->
        if (element === psiFile) {
          sink.addPresentation(InlineInlayPosition(1, false), hintFormat = HintFormat.default) {
            text("inlay text")
          }
        }
      },
    )

    @Test
    fun `failing provider does not stop other providers`() = timeoutRunBlocking {
      val (loggedError, hints) = runHighlighting()
      assertSame(error, loggedError.cause)
      assertEquals(listOf(1 to "inlay text"), hints)
    }
  }

  @Nested
  inner class SharedCollectorFailure {
    private var workingCalls = 0
    private var brokenCalls = 0

    @Suppress("unused")
    private val workingProvider by factoryFixture.inlayHintProviderFixture(
      "working",
      collect = { element, sink ->
        workingCalls++
        sink.addPresentation(InlineInlayPosition(element.textRange.startOffset, false), hintFormat = HintFormat.default) {
          text("working")
        }
      },
    )

    @Suppress("unused")
    private val brokenProvider by factoryFixture.inlayHintProviderFixture(
      "broken",
      collect = { _, sink ->
        if (brokenCalls++ > 0) {
          throw error
        }
        sink.addPresentation(InlineInlayPosition(0, false), hintFormat = HintFormat.default) {
          text("broken")
        }
      },
    )

    @Test
    fun `shared collector failure keeps its hints and other collectors continue`() = timeoutRunBlocking {
      val (loggedError, hints) = runHighlighting()
      assertSame(error, loggedError.cause)
      assertEquals(2, brokenCalls)
      assertTrue(workingCalls > brokenCalls, "The working collector must visit elements after the failure")
      val texts = hints.map { it.second }
      assertEquals(1, texts.count { it == "broken" })
      assertEquals(workingCalls, texts.count { it == "working" })
      assertEquals(workingCalls + 1, texts.size)
    }
  }

  private suspend fun runHighlighting(): Pair<Throwable, List<Pair<Int, String>>> {
    suspendUntilIndexesAreReady(projectFixture.get())
    return withContext(Dispatchers.EDT) {
      val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
        CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false, false)
      }
      loggedError to editor.inlineDeclarativeHintInlays.map { it.offset to it.toText() }
    }
  }
}

/**
 * Adds a provider that uses [collect] to this factory until teardown.
 */
internal fun TestFixture<TestInlayHintsProviderFactory>.inlayHintProviderFixture(
  providerId: String,
  collect: (PsiElement, InlayTreeSink) -> Unit,
): TestFixture<InlayHintsProvider> = testFixture {
  val factory = this@inlayHintProviderFixture.init()
  val provider = object : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
      return object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) = collect(element, sink)

        override fun collectFromElementForActions(element: PsiElement, sink: InlayTreeSink) {}
      }
    }
  }
  val providerInfo = InlayProviderInfo(provider, providerId, emptySet(), true, providerId)
  factory.providers.add(providerInfo)
  initialized(provider) {
    factory.providers.remove(providerInfo)
  }
}

internal class TestInlayHintsProviderFactory(private val providerLanguage: Language) : InlayHintsProviderFactory {
  val providers = CopyOnWriteArrayList<InlayProviderInfo>()

  override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> =
    if (language == providerLanguage) providers.toList() else emptyList()

  override fun getSupportedLanguages(): Set<Language> = setOf(providerLanguage)

  override fun getProviderInfo(language: Language, providerId: String): InlayProviderInfo? =
    getProvidersForLanguage(language).find { it.providerId == providerId }
}
