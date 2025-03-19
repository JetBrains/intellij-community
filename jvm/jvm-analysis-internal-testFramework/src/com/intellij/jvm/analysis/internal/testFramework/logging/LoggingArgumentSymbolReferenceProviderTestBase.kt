package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.analysis.logging.resolve.LoggingArgumentSymbol
import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression

abstract class LoggingArgumentSymbolReferenceProviderTestBase : LightJvmCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST_WITH_LATEST_JDK

  override fun setUp() {
    super.setUp()
    LoggingTestUtils.addSlf4J(myFixture)
    LoggingTestUtils.addLog4J(myFixture)
    LoggingTestUtils.addJUL(myFixture)
    LoggingTestUtils.addKotlinAdapter(myFixture)
  }

  protected fun doTest(bindings: Map<TextRange, String>) {
    val literalExpression = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiLanguageInjectionHost::class.java)
    TestCase.assertFalse(literalExpression == null)
    val refs: Collection<PsiSymbolReference> = PsiSymbolReferenceService.getService().getReferences(literalExpression!!)
    TestCase.assertEquals(bindings.size, refs.size)
    val usedRanges = mutableSetOf<TextRange>()
    refs.forEach { ref ->
      assertEquals(literalExpression, ref.element)
      val symbols = ref.resolveReference()
      assertEquals(1, symbols.size)
      val symbol = symbols.single()
      assertTrue(symbol is LoggingArgumentSymbol)
      val formatSymbol = symbol as LoggingArgumentSymbol
      val logString = formatSymbol.getPlaceholderString()
      assertTrue(logString is ULiteralExpression || logString is UPolyadicExpression)
      val expressionText = formatSymbol.expression.text
      TestCase.assertFalse(ref.rangeInElement in usedRanges)
      assertEquals(bindings[ref.rangeInElement], expressionText)
      usedRanges.add(ref.rangeInElement)
    }
    TestCase.assertTrue(bindings.map { it.key }.toSet() == usedRanges)
  }
}