// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.awt.datatransfer.DataFlavor
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@TestApplication
class CreateTBXReferenceTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)
  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/copyTBXReference/"
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false)
  }

  @Test
  fun testMethodReference(): Unit =
    doTest("fqn=C2#C2")

  @Test
  fun testSelection(): Unit =
    doTest("fqn=X&selection=3:3-7:3")

  @Test
  fun testMultipleSelections(): Unit =
    doTest("path=MultipleSelections.java:11:5&selection1=2:5-4:5&selection2=5:5-7:5&selection3=8:5-10:5&selection4=11:5-13:5")

  @Test
  fun testGroovyConstantReference(): Unit =
    doTest("fqn=BuildOptions#USE_COMPILED_CLASSES_PROPERTY", fileName = "$testName.groovy")

  @Test
  fun testPathWithLocation(): Unit =
    doTest("path=PathWithLocation.java:4:13")

  @Test
  fun testMultipleFiles(): Unit =
    doTest("path1=File1.java&path2=File2.java", fileName = null, psiElements = arrayOf(
      myFixture.configureByFile("File1.java"),
      myFixture.configureByFile("File2.java"))
    )

  private fun doTest(
    expectedParameters: String,
    fileName: String? = "$testName.java",
    psiElements: Array<PsiElement>? = null
  ) {
    fileName?.let {
      myFixture.configureByFile(it)
    }

    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val dataContext = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(myFixture.editor.component))
          .apply {
            if (psiElements != null) {
              add(LangDataKeys.PSI_ELEMENT_ARRAY, psiElements)
            }
          }
          .build()

        val action = ActionManager.getInstance().getAction("CopyTBXReference")

        val presentation = Presentation()
        presentation.copyFrom(action.templatePresentation)
        action.actionPerformed(AnActionEvent(dataContext, presentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null, 0, ActionManager.getInstance()))
      }
    }

    val content = CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor) as String
    val url = URLDecoder.decode(content, StandardCharsets.UTF_8)
    assertThat(url).startsWith("jetbrains://idea/navigate/reference?project=${myFixture.project.name}")
    assertThat(url).endsWith("&${expectedParameters}")
  }
}
