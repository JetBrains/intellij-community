// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightCompletionFixture
import com.intellij.codeInsight.completion.StatisticsUpdate
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the Java completion tests that check the order of the lookup items. Create it with [completionSortingFixture].
 *
 * The completion of [complete], [configureByFile] and [invokeCompletion] is of the type that the factory gets.
 * The members work in the editor of [fixture]. Call them on the EDT.
 */
class CompletionSortingFixture internal constructor(
  /**
   * The completion fixture of the test.
   */
  val completion: LightCompletionFixture,
  private val type: CompletionType,
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
   * Runs the completion and keeps the result in [items].
   */
  fun complete() {
    items = if (type == CompletionType.BASIC) fixture.completeBasic() else fixture.complete(type)
  }

  /**
   * Copies [path] of the test data to the project with its short name, opens it in the editor, and calls [complete].
   */
  fun configureByFile(path: String) {
    configureNoCompletion(path)
    complete()
  }

  /**
   * Copies [path] of the test data to the project with its short name, and opens it in the editor.
   */
  fun configureNoCompletion(path: String) {
    fixture.configureFromExistingVirtualFile(fixture.copyFileToProject(path, StringUtil.getShortName(path, '/')))
  }

  /**
   * Completes in `<test name>.java` and checks the lookup items with [assertPreferredItems].
   */
  fun checkPreferredItems(selected: Int, vararg expected: String) {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(selected, *expected)
  }

  /**
   * Checks that the lookup starts with [expected] and that the item [selected] is selected.
   */
  fun assertPreferredItems(selected: Int, vararg expected: String) {
    fixture.assertPreferredCompletionItems(selected, *expected)
  }

  /**
   * Opens [path] with [configureNoCompletion], runs the completion, and returns the active lookup.
   */
  fun invokeCompletion(path: String): LookupImpl? {
    configureNoCompletion(path)
    fixture.complete(type)
    return getLookup()
  }

  /**
   * Selects the item [index] for the statistics, hides the lookup, and calls [complete].
   */
  fun incUseCount(index: Int) {
    imitateItemSelection(requireNotNull(getLookup()), index)
    requireNotNull(getLookup()).hideLookup(true)
    complete()
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
     * Makes the item [index] of [lookup] the current item, and applies the statistics of its selection.
     */
    @JvmStatic
    fun imitateItemSelection(lookup: LookupEx, index: Int) {
      val item = lookup.items[index]
      lookup.currentItem = item
      StatisticsUpdate.collectStatisticChanges(item)
      StatisticsUpdate.applyLastCompletionStatisticsUpdate()
    }
  }
}

/**
 * Creates a [CompletionSortingFixture] for one test over [lightCompletionFixture]. Declare it as an instance field.
 *
 * The completion is of [type], and the test data path is [testDataPath].
 * After the test, the fixture turns off the lexicographic sort of the lookup.
 */
fun completionSortingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  type: CompletionType,
  testDataPath: String,
): TestFixture<CompletionSortingFixture> = testFixture("completionSortingFixture") {
  val completion = lightCompletionFixture(projectFixture, testDataPath).init()
  initialized(CompletionSortingFixture(completion, type)) {
    UISettings.getInstance().sortLookupElementsLexicographically = false
  }
}
