// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.idea.TestFor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
@TestFor(issues = ["IJPL-261920"])
internal class InjectedIdentifierHighlightingCacheTest {
  companion object {
    private val projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true)
    private val sourceRoot: TestFixture<PsiDirectory> = projectFixture.moduleFixture().sourceRootFixture()
  }

  private val fileFixture: TestFixture<PsiFile> = sourceRoot.psiFileFixture(
    "Host.java",
    """
      class Host {
        String declarations = "int first = 1; int second = 2;";
        String usages = "int sum = first + second + first + second;";
      }
    """.trimIndent(),
  )
  private val injectionDisposableFixture: TestFixture<Disposable> = testFixture {
    fileFixture.init()
    val disposable = Disposer.newDisposable()
    initialized(disposable) {
      withContext(Dispatchers.EDT) {
        Disposer.dispose(disposable)
      }
    }
  }
  private val editorFixture: TestFixture<Editor> = fileFixture.editorFixture()

  @ParameterizedTest
  @ValueSource(strings = ["first", "second", "int", "="])
  fun `caret moves within an injection`(initialText: String): Unit =
    timeoutRunBlocking(context = Dispatchers.Default) {
      val editor = configureInjection()
      val text = readAction { editor.document.text }
      val initialResult = markup(editor, text.indexOf(initialText, text.indexOf("int first")) + 1)
      val expectedInitial = if (initialText == "first" || initialText == "second") ranges(text, initialText) else emptyList()
      assertEquals(expectedInitial, initialResult)
      assertEquals(ranges(text, "first"), markup(editor, text.indexOf("first") + 2))
      assertEquals(ranges(text, "second"), markup(editor, text.indexOf("second") + 2))
    }

  @Test
  fun `caret moves from the host into an injection`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val editor = configureInjection(hostPrefix = "int ")
    val text = readAction { editor.document.text }
    assertEquals(emptyList<TextRange>(), markup(editor, text.indexOf("int first")))
    assertEquals(ranges(text, "first"), markup(editor, text.indexOf("first") + 2))
    assertEquals(ranges(text, "second"), markup(editor, text.indexOf("second") + 2))
  }

  @Test
  fun `custom handler does not cache the host token`(): Unit =
    timeoutRunBlocking(context = Dispatchers.Default) {
      val editor = configureInjection()
      val manager = InjectedLanguageManager.getInstance(projectFixture.get())
      val factory = HighlightUsagesHandlerFactory { handlerEditor, file ->
        object : HighlightUsagesHandlerBase<PsiElement>(handlerEditor, file) {
          override fun getTargets(): List<PsiElement> = listOfNotNull(manager.findInjectedElementAt(file, handlerEditor.caretModel.offset))

          override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
          }

          override fun computeUsages(targets: List<PsiElement>) {
            targets.forEach { addOccurrence(it) }
          }
        }
      }
      ExtensionTestUtil.maskExtensions(HighlightUsagesHandlerFactory.EP_NAME, listOf(factory), injectionDisposableFixture.get())
      val text = readAction { editor.document.text }
      assertEquals(ranges(text, "first").take(1), markup(editor, text.indexOf("first") + 2))
      assertEquals(ranges(text, "second").take(1), markup(editor, text.indexOf("second") + 2))
    }

  private suspend fun configureInjection(hostPrefix: String = ""): Editor {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      InjectedLanguageManager.getInstance(project).registerMultiHostInjector(object : MultiHostInjector {
        override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
          if (context is PsiLanguageInjectionHost && context.isValidHost) {
            val file = context.containingFile
            val hosts = PsiTreeUtil.findChildrenOfType(file, PsiLanguageInjectionHost::class.java).toList()
            registrar.startInjecting(file.language)
            for ((index, host) in hosts.withIndex()) {
              val valueRange = ElementManipulators.getValueTextRange(host)
              val injectedRange = if (index == 0) TextRange(valueRange.startOffset + hostPrefix.length, valueRange.endOffset) else valueRange
              registrar.addPlace(
                if (index == 0) "class Injected { void test() { $hostPrefix" else null,
                if (index == hosts.lastIndex) " } }" else null,
                host,
                injectedRange,
              )
            }
            registrar.doneInjecting()
          }
        }

        override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(PsiLanguageInjectionHost::class.java)
      }, injectionDisposableFixture.get())
    }
    suspendUntilIndexesAreReady(project)
    return editorFixture.get()
  }

  private suspend fun markup(editor: Editor, offset: Int): List<TextRange> {
    withContext(Dispatchers.EDT) {
      editor.caretModel.moveToOffset(offset)
    }
    val visibleRange = readAction { ProperTextRange(0, editor.document.textLength) }
    val result = IdentifierHighlightingManager.getInstance(projectFixture.get()).getMarkupData(editor, visibleRange)
    return readAction { result.occurrences.map { TextRange.create(it.range) }.sortedBy { it.startOffset } }
  }

  private fun ranges(text: String, identifier: String): List<TextRange> =
    Regex("\\b$identifier\\b").findAll(text).map { TextRange(it.range.first, it.range.last + 1) }.toList()
}
