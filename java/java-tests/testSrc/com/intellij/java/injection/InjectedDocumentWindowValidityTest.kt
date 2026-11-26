// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.injection

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.ReadActionCache
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * `JavadocInjector` registers one place for each line of a `{@snippet}` body.
 * So the snippet of [MANY_PLACES] lines gives an injected document window with [MANY_PLACES] places.
 */
@TestApplication
internal class InjectedDocumentWindowValidityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val MANY_PLACES = 2_000
    private const val CALLS = 10_000
    private const val INJECT_PREFIX = "inject:"
  }

  private val fixture by codeInsightModuleFixture(projectFixture)

  @Test
  @PerformanceUnitTest
  fun `isValid of a window with many places costs about as much as isValid of a window with one place in one read action`() {
    configure(onePlace = 1, manyPlaces = MANY_PLACES)
    val onePlace = injectedWindow("onePlace")
    val manyPlaces = injectedWindow("manyPlaces")
    assertThat(runReadAction { onePlace.window.hostRanges }).hasSize(1)
    assertThat(runReadAction { manyPlaces.window.hostRanges }).hasSize(MANY_PLACES)

    assertCostsAboutAsMuch("isValid()",
                           onePlace = { check(onePlace.window.isValid) },
                           manyPlaces = { check(manyPlaces.window.isValid) })
  }

  @Test
  fun `a window that is valid in one read action is invalid in the next read action after the deletion of its host`() {
    configure(onePlace = 1, manyPlaces = 3)
    val injected = injectedWindow("manyPlaces")
    assertThat(runReadAction { injected.window.isValid }).isTrue()

    EdtTestUtil.runInEdtAndWait<Throwable> {
      WriteCommandAction.runWriteCommandAction(fixture.project) {
        method("manyPlaces").docComment!!.delete()
        PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
      }
    }

    assertThat(runReadAction { injected.window.isValid }).isFalse()
  }

  @Test
  fun `a window that is valid is invalid after an uncommitted deletion of its host text`() {
    configure(onePlace = 1, manyPlaces = 3)
    val injected = injectedWindow("manyPlaces")
    assertThat(runReadAction { injected.window.isValid }).isTrue()

    EdtTestUtil.runInEdtAndWait<Throwable> {
      WriteCommandAction.runWriteCommandAction(fixture.project) {
        val docComment = method("manyPlaces").docComment!!.textRange
        fixture.editor.document.deleteString(docComment.startOffset, docComment.endOffset)
      }
    }

    assertThat(PsiDocumentManager.getInstance(fixture.project).isUncommited(fixture.editor.document)).isTrue()
    assertThat(runReadAction { injected.window.isValid }).isFalse()
  }

  @Test
  fun `a window that is valid under the write-intent lock is invalid after a nested write action deletes its host`() {
    configure(onePlace = 1, manyPlaces = 3)
    val injected = injectedWindow("manyPlaces")

    EdtTestUtil.runInEdtAndWait<Throwable> {
      WriteIntentReadAction.run {
        ReadActionCache.getInstance().allowInWriteAction {
          assertThat(ApplicationManager.getApplication().isWriteAccessAllowed).isFalse()
          assertThat(injected.window.isValid).isTrue()
          WriteCommandAction.runWriteCommandAction(fixture.project) {
            method("manyPlaces").docComment!!.delete()
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
          }
          assertThat(ApplicationManager.getApplication().isWriteAccessAllowed).isFalse()
          assertThat(injected.window.isValid).isFalse()
        }
      }
    }
  }

  /**
   * The reparse replaces the comment that hosts the injection.
   * Until the commit is published, the smart pointer of the host holds the range of the old comment and finds no element.
   */
  @Test
  fun `a window keeps no result of a check during the host reparse`() {
    val disposable = Disposer.newDisposable()
    try {
      InjectedLanguageManager.getInstance(fixture.project).registerMultiHostInjector(CommentInjector(), disposable)
      EdtTestUtil.runInEdtAndWait<Throwable> {
        fixture.configureByText("A.java", "class A {\n  void m() {\n    // inject:abc\n  }\n}\n")
      }
      val injected = commentInjectedWindow()
      assertThat(runReadAction { injected.window.isValid }).isTrue()

      Registry.get("platform.random.idempotence.check.rate").setValue(1, disposable)
      val validityDuringReparse = mutableListOf<Boolean>()
      PsiManager.getInstance(fixture.project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
        override fun childReplaced(event: PsiTreeChangeEvent) {
          validityDuringReparse.add(injected.window.isValid)
        }
      }, disposable)
      val errors = mutableListOf<String>()
      LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
          errors.add(message)
          return Action.NONE
        }
      }) {
        EdtTestUtil.runInEdtAndWait<Throwable> {
          WriteCommandAction.runWriteCommandAction(fixture.project) {
            val document = fixture.editor.document
            document.insertString(document.text.indexOf("abc") + "abc".length, "def")
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
          }
        }
      }

      assertThat(validityDuringReparse).isNotEmpty()
      assertThat(errors).isEmpty()
      assertThat(runReadAction { injected.window.isValid }).isTrue()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * Compares the time of [CALLS] calls of [onePlace] and of [manyPlaces].
   * A check of every place makes the many-place calls cost hundreds of times more.
   */
  private fun assertCostsAboutAsMuch(name: String, onePlace: () -> Unit, manyPlaces: () -> Unit) {
    repeat(3) {
      nanosOfCalls(onePlace)
      nanosOfCalls(manyPlaces)
    }
    val onePlaceNanos = nanosOfCalls(onePlace)
    val manyPlacesNanos = nanosOfCalls(manyPlaces)

    val limitNanos = maxOf(onePlaceNanos * 20, TimeUnit.MILLISECONDS.toNanos(200))
    assertThat(manyPlacesNanos)
      .describedAs("$CALLS $name calls: one place ${onePlaceNanos / 1000} µs, $MANY_PLACES places ${manyPlacesNanos / 1000} µs")
      .isLessThan(limitNanos)
  }

  /**
   * Runs [call] [CALLS] times in one read action on this background thread.
   * A read action on the EDT under the write-intent lock has no read-action cache, so the calls must not run there.
   */
  private fun nanosOfCalls(call: () -> Unit): Long = runReadAction {
    val start = System.nanoTime()
    repeat(CALLS) { call() }
    System.nanoTime() - start
  }

  private fun configure(onePlace: Int, manyPlaces: Int) {
    val text = "class A {\n${snippetMethod("onePlace", onePlace)}${snippetMethod("manyPlaces", manyPlaces)}}\n"
    EdtTestUtil.runInEdtAndWait<Throwable> {
      fixture.configureByText("A.java", text)
    }
  }

  private fun snippetMethod(name: String, lines: Int): String = buildString {
    append("  /**\n")
    append("   * {@snippet lang=plain :\n")
    repeat(lines) { append("   * line $it\n") }
    append("   * }\n")
    append("   */\n")
    append("  void $name() {}\n")
  }

  private fun method(name: String): PsiMethod = JavaPsiFacade.getInstance(fixture.project).findClass("A", GlobalSearchScope.allScope(fixture.project))!!
    .findMethodsByName(name, false).single()

  private fun injectedWindow(methodName: String): InjectedWindow = runReadAction {
    val snippet = PsiTreeUtil.findChildOfType(method(methodName).docComment, PsiSnippetDocTag::class.java)!!
    var injectedFile: PsiFile? = null
    InjectedLanguageManager.getInstance(fixture.project).enumerate(snippet) { injectedPsi, _ -> injectedFile = injectedPsi }
    val file = injectedFile!!
    InjectedWindow(file, PsiDocumentManager.getInstance(fixture.project).getCachedDocument(file) as DocumentWindow)
  }

  private fun commentInjectedWindow(): InjectedWindow = runReadAction {
    val comment = PsiTreeUtil.findChildOfType(fixture.file, PsiComment::class.java)!!
    var injectedFile: PsiFile? = null
    InjectedLanguageManager.getInstance(fixture.project).enumerate(comment) { injectedPsi, _ -> injectedFile = injectedPsi }
    val file = injectedFile!!
    InjectedWindow(file, PsiDocumentManager.getInstance(fixture.project).getCachedDocument(file) as DocumentWindow)
  }

  /** Injects plain text after the `inject:` prefix of a comment. */
  private class CommentInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
      val prefix = context.text.indexOf(INJECT_PREFIX)
      if (prefix < 0) return
      registrar.startInjecting(PlainTextLanguage.INSTANCE)
        .addPlace(null, null, context as PsiLanguageInjectionHost, TextRange(prefix + INJECT_PREFIX.length, context.textLength))
        .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(PsiComment::class.java)
  }

  /** Holds the injected [file], so that its view provider stays cached while the test checks the [window]. */
  private class InjectedWindow(val file: PsiFile, val window: DocumentWindow)
}
