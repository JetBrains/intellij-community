// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

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

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}