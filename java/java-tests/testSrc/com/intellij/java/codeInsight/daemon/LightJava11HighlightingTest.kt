// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.java.codeserver.core.JavaPsiSingleFileSourceUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class LightJava11HighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_11)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting11"
  }

  @Test
  fun testMixedVarAndExplicitTypesInLambdaDeclaration(): Unit = doTest()

  @Test
  fun testGotoDeclarationOnLambdaVarParameter(): Unit = timeoutRunBlocking {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    withContext(Dispatchers.EDT) {
      val offset = myFixture.editor.caretModel.offset
      val elements = GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, offset)
      assertThat(elements).hasSize(1)
      val element = elements[0]
      assertThat(element).isInstanceOf(PsiClass::class.java)
      assertEquals(CommonClassNames.JAVA_LANG_STRING, (element as PsiClass).qualifiedName)
    }
  }

  @Test
  fun testTryWithResourcesWithIntersectionType() {
    doTest()
  }

  @Test
  fun testShebangInJavaFile() {
    doTest()
  }

  @Test
  fun testComplexStreamTypeMismatch() {
    doTest()
  }

  @Test
  fun testStaticImportArrayCopyOfAccess() {
    doTest()
  }

  @Test
  fun testJavaShebang(): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("hello",
                                         """#!/path/to/java
                                 |class Main {{
                                 |int i = 0;
                                 |}}""".trimMargin())
    myFixture.checkHighlighting()
    assertTrue(readAction { JavaPsiSingleFileSourceUtil.isJavaHashBangScript(file) })
  }

  @Test
  fun testRequiresJavaBase() {
    myFixture.configureByText("module-info.java", """
      module M {
        requires <error descr="Modifier 'static' not allowed here">static</error> <error descr="Modifier 'transitive' not allowed here">transitive</error> java.base;
      }""".trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun testRequiresTransitiveJavaBaseWithStatic() {
    IdeaTestUtil.withLevel(myFixture.module, JavaFeature.TRANSITIVE_DEPENDENCY_ON_JAVA_BASE.standardLevel!!){
      myFixture.configureByText("module-info.java", """
      module M {
        requires <error descr="Modifier 'static' not allowed here">static</error> transitive java.base;
      }""".trimIndent())
      myFixture.checkHighlighting()
    }
  }

  @Test
  fun testRequiresTransitiveJavaBase() {
    IdeaTestUtil.withLevel(myFixture.module, JavaFeature.TRANSITIVE_DEPENDENCY_ON_JAVA_BASE.standardLevel!!){
      myFixture.configureByText("module-info.java", """
      module M {
        requires transitive java.base;
      }""".trimIndent())
      myFixture.checkHighlighting()
    }
  }

  private fun doTest() {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}
