// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert

class LightJava11HighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_11
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting11"

  fun testMixedVarAndExplicitTypesInLambdaDeclaration() = doTest()

  fun testGotoDeclarationOnLambdaVarParameter() {
    myFixture.configureByFile(getTestName(false) + ".java")
    val offset = myFixture.editor.caretModel.offset
    val elements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, offset)
    assertThat(elements).hasSize(1)
    val element = elements[0]
    assertThat(element).isInstanceOf(PsiClass::class.java)
    assertEquals(CommonClassNames.JAVA_LANG_STRING, (element as PsiClass).qualifiedName)
  }

  fun testTryWithResourcesWithIntersectionType() {
    doTest()
  }

  fun testShebangInJavaFile() {
    doTest()
  }

  fun testComplexStreamTypeMismatch() {
    doTest()
  }

  fun testStaticImportArrayCopyOfAccess() {
    doTest()
  }

  fun testJavaShebang() {
    val file = myFixture.configureByText("hello",
                                         """#!/path/to/java
                                 |class Main {{
                                 |int i = 0;
                                 |}}""".trimMargin())
    myFixture.checkHighlighting()
    Assert.assertTrue(JavaHighlightUtil.isJavaHashBangScript(file))
  }

  fun testRequiresJavaBase() {
    myFixture.configureByText("module-info.java", """
      module M {
        requires <error descr="Modifier 'static' not allowed here">static</error> <error descr="Modifier 'transitive' not allowed here">transitive</error> java.base;
      }""".trimIndent())
    myFixture.checkHighlighting()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}