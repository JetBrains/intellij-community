package com.intellij.jvm.analysis.testFramework

import com.intellij.ide.actions.GotoRelatedSymbolAction
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

/**
 * A test base for testing [com.intellij.navigation.GotoRelatedProvider] implementations in all JVM languages.
 */
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

  protected fun JavaCodeInsightTestFixture.testGoToPsiElement(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiElement) -> Unit
  ) {
    configureByText("$fileName${lang.ext}", before)
    val offset = getCaretOffset()
    val referenceAt = myFixture.file.findReferenceAt(offset)
    val resolved = referenceAt!!.resolve()!!
    assertion(resolved)
  }

  private fun getCaretOffset(): Int {
    return myFixture.editor.caretModel.primaryCaret.offset
  }
}