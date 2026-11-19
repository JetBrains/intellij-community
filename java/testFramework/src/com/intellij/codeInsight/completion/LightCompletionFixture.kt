// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.waitForAllDocumentsCommitted
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import java.util.concurrent.TimeUnit

/**
 * The fixture of a Java completion test. Create it with [lightCompletionFixture].
 *
 * The members complete, select and type in the editor of [fixture]. Call them on the EDT.
 */
@TestOnly
open class LightCompletionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * The items of the last [complete] call, or `null` when the completion inserted the single item.
   */
  var items: Array<LookupElement>? = null

  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Copies [path] of the test data to the project with its short name, opens it in the editor, and calls [complete].
   */
  fun configureByFile(path: String) {
    fixture.configureFromExistingVirtualFile(fixture.copyFileToProject(path, StringUtil.getShortName(path, '/')))
    complete()
  }

  /**
   * Calls [configureByFile] with `/<test name>.java`.
   */
  fun configureByTestName() {
    configureByFile("/" + getTestName(false) + ".java")
  }

  /**
   * Checks that the completion in `<test name>.java` changes nothing and shows no items and no lookup.
   */
  fun doAntiTest() {
    configureByTestName()
    checkResultByFile(getTestName(false) + ".java")
    assertNotNull(items)
    assertEquals(emptyList<LookupElement>(), items!!.toList())
    assertNull(getLookup())
  }

  /**
   * Runs the basic completion and keeps the result in [items].
   */
  fun complete() {
    items = fixture.completeBasic()
  }

  /**
   * Selects [item] in the active lookup and inserts it.
   */
  fun selectItem(item: LookupElement) {
    selectItem(item, 0.toChar())
  }

  /**
   * Selects [item] in the active lookup. Then it finishes the lookup with [completionChar], or types [completionChar].
   * After that, it waits for the asynchronous tasks and for the document commits.
   */
  fun selectItem(item: LookupElement, completionChar: Char) {
    val lookup = requireNotNull(getLookup()) { "No active lookup" }
    lookup.currentItem = item
    if (LookupEvent.isSpecialCompletionChar(completionChar)) {
      lookup.finishLookup(completionChar)
    }
    else {
      type(completionChar)
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
  }

  /**
   * Checks the text of the editor against [path] of the test data.
   */
  fun checkResultByFile(path: String) {
    fixture.checkResultByFile(path)
  }

  /**
   * Returns the active lookup, or `null` when no lookup shows.
   */
  fun getLookup(): LookupImpl? = LookupManager.getInstance(fixture.project).activeLookup as LookupImpl?

  /**
   * Checks that the lookup starts with [items], in this order.
   */
  fun assertFirstStringItems(vararg items: String) {
    val strings = fixture.lookupElementStrings
    assertNotNull(strings)
    assertEquals(items.toList(), strings!!.take(items.size))
  }

  /**
   * Checks that the lookup has exactly [items], in this order.
   */
  fun assertStringItems(vararg items: String) {
    val strings = fixture.lookupElementStrings
    assertNotNull(strings)
    assertEquals(items.toList(), strings)
  }

  /**
   * Types [s] in the editor.
   */
  fun type(s: String) {
    fixture.type(s)
  }

  /**
   * Types [c] in the editor.
   */
  fun type(c: Char) {
    fixture.type(c)
  }
}

/**
 * Creates the project fixture of a completion test class: [lightJavaProjectFixture] with [descriptor].
 * The default is [LightJavaCodeInsightFixtureTestCase.JAVA_1_6], the descriptor of the JUnit 3 completion tests.
 */
@TestOnly
@JvmOverloads
fun lightCompletionProjectFixture(
  descriptor: LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_1_6,
): TestFixture<LightJavaProject> = lightJavaProjectFixture(descriptor)

/**
 * Creates a [LightCompletionFixture] for one test. Declare it as an instance field.
 *
 * The fixture turns on the completion statistics for the test.
 * After the test, it sets the case sensitivity of the completion back to [CodeInsightSettings.FIRST_LETTER].
 * The test data path is [testDataPath], or the community path when it is `null`.
 */
@TestOnly
@JvmOverloads
fun lightCompletionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<LightCompletionFixture> = testFixture("lightCompletionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  (StatisticsManager.getInstance() as StatisticsManagerImpl).enableStatistics(fixture.testRootDisposable)
  initialized(LightCompletionFixture(fixture)) {
    CodeInsightSettings.getInstance().completionCaseSensitive = CodeInsightSettings.FIRST_LETTER
  }
}
