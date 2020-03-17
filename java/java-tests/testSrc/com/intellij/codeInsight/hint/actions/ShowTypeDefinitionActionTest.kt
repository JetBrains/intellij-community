// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint.actions

import com.intellij.JavaTestUtil
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class ShowTypeDefinitionActionTest : JavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/showTypeDefinition/"
  }

  fun testReference() {
    myFixture.configureByFile(getTestName(false) + ".java")
    val definitions = showDefinitions()
    assertEquals(1, definitions.size)
    val psiClass = definitions[0] as? PsiClass
    assertNotNull(psiClass)
    assertEquals("String", psiClass!!.name)
    assertEquals("String.java", psiClass.containingFile!!.name)
  }

  fun testNoDefinitionForInt() {
    doTestNoDefinitions()
  }

  fun testNoDefinitionForClass() {
    doTestNoDefinitions()
  }

  fun testNoDefinitionForMethod() {
    doTestNoDefinitions()
  }

  fun testNoDefinitionForType() {
    doTestNoDefinitions()
  }

  private fun doTestNoDefinitions() {
    myFixture.configureByFile(getTestName(false) + ".java")
    assertEmpty(showDefinitions())
  }

  private fun showDefinitions() = ShowTypeDefinitionAction.runForTests(myFixture.editor.contentComponent)
}