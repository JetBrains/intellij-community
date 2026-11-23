// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.indexing.DumbModeAccessType

/**
 * The fixture of the Java completion tests that compare the editor with `<test name>_after.java`.
 * Create it with [normalCompletionFixture].
 *
 * The members complete, select and type in the editor of [fixture], as the members of [LightCompletionFixture] do. Call them on the EDT.
 */
class NormalCompletionFixture internal constructor(
  /**
   * The completion fixture of the test.
   */
  val completion: LightCompletionFixture,
) {
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture
    get() = completion.fixture

  /**
   * The items of the last [complete] call, or `null` when the completion inserted the single item.
   */
  var items: Array<LookupElement>?
    get() = completion.items
    set(value) {
      completion.items = value
    }

  /**
   * Returns the name of the test method without the `test` prefix.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = completion.getTestName(lowercaseFirstLetter)

  /**
   * Copies [path] of the test data to the project with its short name, opens it in the editor, and calls [complete].
   */
  fun configureByFile(path: String) {
    completion.configureByFile(path)
  }

  /**
   * Calls [configureByFile] with `/<test name>.java`.
   */
  fun configureByTestName() {
    completion.configureByTestName()
  }

  /**
   * Calls [configureByTestName].
   */
  fun configure() {
    configureByTestName()
  }

  /**
   * Checks the text of the editor against `<test name>_after.java`.
   */
  fun checkResult() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  /**
   * Calls [configure], types [finishChar], waits for the asynchronous tasks, and calls [checkResult].
   */
  fun doTest(finishChar: String) {
    configure()
    type(finishChar)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    checkResult()
  }

  /**
   * Calls [configure] and [checkResult].
   */
  fun doTest() {
    configure()
    checkResult()
  }

  /**
   * Checks that the completion in `<test name>.java` changes nothing and shows no items and no lookup.
   */
  fun doAntiTest() {
    completion.doAntiTest()
  }

  /**
   * Runs the basic completion and keeps the result in [items].
   */
  fun complete() {
    completion.complete()
  }

  /**
   * Selects [item] in the active lookup and inserts it.
   */
  fun selectItem(item: LookupElement) {
    completion.selectItem(item)
  }

  /**
   * Selects [item] in the active lookup. Then it finishes the lookup with [completionChar], or types [completionChar].
   */
  fun selectItem(item: LookupElement, completionChar: Char) {
    completion.selectItem(item, completionChar)
  }

  /**
   * Checks the text of the editor against [path] of the test data.
   */
  fun checkResultByFile(path: String) {
    completion.checkResultByFile(path)
  }

  /**
   * Returns the active lookup, or `null` when no lookup shows.
   */
  fun getLookup(): LookupImpl? = completion.getLookup()

  /**
   * Checks that the lookup starts with [items], in this order.
   */
  fun assertFirstStringItems(vararg items: String) {
    completion.assertFirstStringItems(*items)
  }

  /**
   * Checks that the lookup has exactly [items], in this order.
   */
  fun assertStringItems(vararg items: String) {
    completion.assertStringItems(*items)
  }

  /**
   * Types [s] in the editor.
   */
  fun type(s: String) {
    completion.type(s)
  }

  /**
   * Types [c] in the editor.
   */
  fun type(c: Char) {
    completion.type(c)
  }

  companion object {
    /**
     * Renders [e] in a read action, with the reliable index data in the dumb mode.
     */
    @JvmStatic
    fun renderElement(e: LookupElement): LookupElementPresentation {
      return ReadAction.computeBlocking<LookupElementPresentation, RuntimeException> {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable<LookupElementPresentation, RuntimeException> {
          LookupElementPresentation.renderElement(e)
        })
      }
    }
  }
}

/**
 * Creates a [NormalCompletionFixture] for one test over [lightCompletionFixture]. Declare it as an instance field.
 *
 * The test data path is [testDataPath], or `codeInsight/completion/normal/` of the Java test data when it is `null`.
 */
@JvmOverloads
fun normalCompletionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<NormalCompletionFixture> = testFixture("normalCompletionFixture") {
  val completion = lightCompletionFixture(projectFixture, testDataPath ?: (JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal/")).init()
  initialized(NormalCompletionFixture(completion)) {}
}
