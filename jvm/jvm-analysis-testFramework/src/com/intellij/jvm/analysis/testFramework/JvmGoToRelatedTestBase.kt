package com.intellij.jvm.analysis.testFramework

import com.intellij.ide.actions.GotoRelatedSymbolAction
import com.intellij.navigation.GotoRelatedItem
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * A test base for testing [com.intellij.navigation.GotoRelatedProvider] implementations in all JVM languages.
 *
 * @deprecated Use [jvmCodeInsightFixture] and [JvmCodeInsightFixture.testGoToRelatedAction] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use jvmCodeInsightFixture in a JUnit 5 test")
abstract class JvmGoToRelatedTestBase : LightJvmCodeInsightFixtureTestCase() {
  protected fun JavaCodeInsightTestFixture.testGoToRelatedAction(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (GotoRelatedItem) -> Unit
  ) {
    configureByText("$fileName${lang.ext}", before)
    val items = GotoRelatedSymbolAction.getItems(myFixture.getFile(), myFixture.getEditor())
    assertSize(1, items)
    assertion(items.first())
  }
}