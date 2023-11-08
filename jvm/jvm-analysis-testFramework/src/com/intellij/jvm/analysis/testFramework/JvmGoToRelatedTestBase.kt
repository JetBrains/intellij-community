package com.intellij.jvm.analysis.testFramework

import com.intellij.ide.actions.GotoRelatedSymbolAction
import com.intellij.navigation.GotoRelatedItem
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

abstract class JvmGoToRelatedTestBase : LightJvmCodeInsightFixtureTestCase() {
  protected fun JavaCodeInsightTestFixture.testGoToRelatedAction(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (GotoRelatedItem) -> Unit
  ) {
    configureByText("$fileName${lang.ext}", before)
    val items = GotoRelatedSymbolAction.getItems(myFixture.getFile(), myFixture.getEditor(), null)
    assertSize(1, items)
    assertion(items.first())
  }
}