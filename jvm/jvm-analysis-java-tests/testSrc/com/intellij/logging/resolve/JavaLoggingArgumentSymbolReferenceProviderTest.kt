package com.intellij.logging.resolve

import com.intellij.analysis.logging.resolve.LoggingArgumentSymbol
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingArgumentSymbolReferenceProviderTestBase
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.uast.ULiteralExpression

class JavaLoggingArgumentSymbolReferenceProviderTest : LoggingArgumentSymbolReferenceProviderTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST_WITH_LATEST_JDK

  fun `test log4j2`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }


  private fun doTest(bindings : Map<TextRange, String>) {
    val literal = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiLiteralExpression::class.java)
    TestCase.assertFalse(literal == null)
    val refs: Collection<PsiSymbolReference> = PsiSymbolReferenceService.getService().getReferences(literal!!)
    refs.forEach {ref ->
      assertEquals(literal, ref.element)
      val symbols = ref.resolveReference()
      assertEquals(bindings.size, symbols.size)
      val symbol = symbols.single()
      assertTrue(symbol is LoggingArgumentSymbol)
      val formatSymbol = symbol as LoggingArgumentSymbol
      assertTrue(formatSymbol.getPlaceholderString() is ULiteralExpression)
      val expressionText = formatSymbol.expression.text
      assertEquals(bindings[ref.rangeInElement], expressionText)
    }
  }
}