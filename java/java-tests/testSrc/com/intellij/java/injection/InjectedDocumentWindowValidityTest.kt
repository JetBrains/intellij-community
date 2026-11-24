// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.injection

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.ReadActionCache
import com.intellij.testFramework.EdtTestUtil
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

  /** Holds the injected [file], so that its view provider stays cached while the test checks the [window]. */
  private class InjectedWindow(val file: PsiFile, val window: DocumentWindow)
}
