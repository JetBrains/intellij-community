// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.hint.actions

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.hint.actions.ShowTypeDefinitionAction
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiClass
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
class ShowTypeDefinitionActionTest {
  private val myFixture by javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/showTypeDefinition/")

  private lateinit var testName: String

  @BeforeEach
  fun setUpTestName(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false)
  }

  @Test
  fun testReference(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.java")
    val definitions = showDefinitions()
    assertEquals(1, definitions.size)
    val psiClass = definitions[0] as? PsiClass
    assertNotNull(psiClass)
    assertEquals("String", psiClass!!.name)
    assertEquals("String.java", psiClass.containingFile!!.name)
  }

  @Test
  fun testNoDefinitionForInt(): Unit = timeoutRunBlocking {
    doTestNoDefinitions()
  }

  @Test
  fun testNoDefinitionForClass(): Unit = timeoutRunBlocking {
    doTestNoDefinitions()
  }

  @Test
  fun testNoDefinitionForMethod(): Unit = timeoutRunBlocking {
    doTestNoDefinitions()
  }

  @Test
  fun testNoDefinitionForType(): Unit = timeoutRunBlocking {
    doTestNoDefinitions()
  }

  private suspend fun doTestNoDefinitions() {
    myFixture.configureByFile("$testName.java")
    assertThat(showDefinitions()).isNullOrEmpty()
  }

  private suspend fun showDefinitions() = withContext(Dispatchers.EDT) { ShowTypeDefinitionAction.runForTests(myFixture.editor.contentComponent) }
}