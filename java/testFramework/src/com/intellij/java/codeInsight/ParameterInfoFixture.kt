// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.AutoPopupParameterInfoTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.EditorHintFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.util.concurrent.locks.LockSupport

/**
 * The fixture of a Java test of the parameter info and the completion. Create it with [parameterInfoFixture].
 *
 * It has the members of [LightCompletionFixture] and the parameter info helpers. Call them on the EDT.
 */
@TestOnly
class ParameterInfoFixture internal constructor(
  fixture: LightJavaCodeInsightTestFixture,
  private val hintFixture: EditorHintFixture,
) : LightCompletionFixture(fixture) {
  /**
   * Opens a Java file with [text] in the editor.
   */
  fun configureJava(text: String) {
    fixture.configureByText(JavaFileType.INSTANCE, text)
  }

  /**
   * Runs the show parameter info action and waits for the hint.
   */
  fun showParameterInfo() {
    fixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
    waitForParameterInfo()
  }

  /**
   * Checks the text of the current hint without the style and the span tags.
   */
  fun checkHintContents(hintText: String?) {
    val hint = hintFixture.currentHintText
      ?.replace(Regex("<style>[^<]*</style>\\s*"), "")
      ?.replace(Regex("</?span[^>]*>"), "")
    assertEquals(hintText, hint)
  }

  /**
   * Checks the text of the editor against [text].
   */
  fun checkResult(text: String) {
    fixture.checkResult(text)
  }

  /**
   * Runs the basic completion, selects the item that contains [partOfItemText], and waits for the parameter info.
   */
  fun complete(partOfItemText: String) {
    val elements = fixture.completeBasic()
    selectItem(findLookupElementContainingText(elements, partOfItemText))
    waitForParameterInfo()
  }

  /**
   * Runs the smart completion.
   */
  fun completeSmart() {
    fixture.complete(CompletionType.SMART)
  }

  /**
   * Runs the smart completion, selects the item that contains [partOfItemText], and waits for the parameter info.
   */
  fun completeSmart(partOfItemText: String) {
    val elements = fixture.complete(CompletionType.SMART)
    selectItem(findLookupElementContainingText(elements, partOfItemText))
    waitForParameterInfo()
  }

  /**
   * Waits for the parameter info update, the highlighting, the hint animation and the auto-popup.
   */
  fun waitForAllAsyncStuff() {
    AutoPopupParameterInfoTestUtil.waitForParameterInfoUpdate(fixture.editor)
    waitForParameterInfo()
    fixture.doHighlighting()
    waitTillAnimationCompletes(fixture.editor)
    AutoPopupParameterInfoTestUtil.waitForAutoPopup(fixture.project)
    waitForParameterInfo()
  }

  companion object {
    /**
     * Waits for the chain of the non-blocking read actions that show the parameter info. Call it on the EDT.
     */
    @JvmStatic
    fun waitForParameterInfo() {
      // effective there is a chain of 5 nonBlockingRead actions
      repeat(5) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }

    /**
     * Returns the item of [lookupItems] whose item text and tail text contain [lookupString].
     */
    @JvmStatic
    fun findLookupElementContainingText(lookupItems: Array<LookupElement>, lookupString: String): LookupElement {
      for (element in lookupItems) {
        val p = LookupElementPresentation()
        element.renderElement(p)
        val elementText = p.itemText + p.tailText
        if (elementText.contains(lookupString)) {
          return element
        }
      }
      val allLookupElements = lookupItems.joinToString(", ") { it.lookupString }
      throw NoSuchElementException("Unable to find a lookup element by '$lookupString' among [$allLookupElements]")
    }

    /**
     * Waits up to 60 s for the end of the parameter hint animation in [editor]. Call it on the EDT.
     */
    @JvmStatic
    fun waitTillAnimationCompletes(editor: Editor) {
      val deadline = System.currentTimeMillis() + 60_000
      while (ParameterHintsPresentationManager.getInstance().isAnimationInProgress(editor)) {
        if (System.currentTimeMillis() > deadline) fail<Unit>("Too long waiting for animation to finish")
        LockSupport.parkNanos(10_000_000)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
    }
  }
}

/**
 * Creates a [ParameterInfoFixture] for one test. Declare it as an instance field.
 *
 * The fixture does what [lightCompletionFixture] does.
 * It also sets the parameter info delay to 100 ms for the test, and restores the old delay after the test.
 */
@TestOnly
@JvmOverloads
fun parameterInfoFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<ParameterInfoFixture> = testFixture("parameterInfoFixture") {
  val fixture = lightCompletionFixture(projectFixture, testDataPath).init().fixture
  val hintFixture = EditorHintFixture(fixture.testRootDisposable)
  val settings = CodeInsightSettings.getInstance()
  val storedAutoPopupDelay = settings.PARAMETER_INFO_DELAY
  settings.PARAMETER_INFO_DELAY = 100 // speed up tests
  initialized(ParameterInfoFixture(fixture, hintFixture)) {
    CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = storedAutoPopupDelay
  }
}
