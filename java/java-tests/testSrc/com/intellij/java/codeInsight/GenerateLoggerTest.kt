// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiClass
import com.intellij.refactoring.introduce.PsiIntroduceTarget
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.UiInterceptors
import com.intellij.ui.UiInterceptors.UiInterceptor
import com.intellij.ui.components.JBList
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.ListModel
import kotlin.time.Duration.Companion.minutes

@TestApplication
class GenerateLoggerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_21)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generateLogger"
  }

  @Test
  fun testSlf4j(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest()
  }

  @Test
  fun testLog4j2(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest()
  }

  @Test
  fun testLog4j(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest()
  }

  @Test
  fun testApacheCommons(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest()
  }

  @Test
  fun testAnonymousClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest()
  }

  @Test
  fun testImplicitlyDeclaredClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    myFixture.configureByText("implicitlyDeclaredClass.java",
                              """
                                void main() {<caret>
                                }
                                """.trimIndent())

    readAction {
      val loggers = JvmLogger.findSuitableLoggers(myFixture.module)
      assertTrue(loggers.isNotEmpty())
      val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
      val places = JvmLogger.getPossiblePlacesForLogger(element, loggers)

      assertTrue(places.isEmpty())
    }
  }

  @Test
  fun testNestedClassesOuterClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Outer"
    )
  }

  @Test
  fun testNestedClassesNestedClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  @Test
  fun testMultipleNestedClasses(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  @Test
  fun testSaveSettings(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    assertEquals(myFixture.project.service<JvmLoggingSettingsStorage>().state.loggerId, UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)
    doTest()
    assertEquals(myFixture.project.service<JvmLoggingSettingsStorage>().state.loggerId, "Log4j")
  }

  @Test
  fun testRespectCustomLoggerName(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val state = myFixture.project.service<JvmLoggingSettingsStorage>().state
    val oldName = state.loggerName
    try {
      JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
      state.loggerName = "CustomName"
      doTest()
    }
    finally {
      state.loggerName = oldName
    }
  }

  private suspend fun doTestWithMultiplePlaces(expectedClassNameList: List<String>, selectedClass: String) {
    val name = myFixture.getTestName(false)
    myFixture.configureByFile("before$name.java")

    UiInterceptors.register(object : UiInterceptor<JBPopup>(JBPopup::class.java) {
      @Suppress("UNCHECKED_CAST")
      override fun doIntercept(popup: JBPopup) {
        Disposer.register(myFixture.testRootDisposable, popup)
        val content = UIUtil.findComponentOfType(popup.getContent(), JBList::class.java)

        assertTrue(content?.selectedIndex == 0)

        val model: ListModel<PsiIntroduceTarget<PsiClass>> = content!!.model as ListModel<PsiIntroduceTarget<PsiClass>>

        val actualClassNameList: MutableList<String> = mutableListOf()

        (0..<model.size).forEach {
          actualClassNameList.add(model.getElementAt(it).render())
        }

        assertEquals(expectedClassNameList, actualClassNameList)

        val indexOfPreferredOption = actualClassNameList.indexOf(selectedClass)

        assertTrue(indexOfPreferredOption >= 0)

        content.selectedIndex = indexOfPreferredOption

        popup.closeOk(null)
      }

    })

    withContext(Dispatchers.EDT) {
      GenerateLoggerHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)

      myFixture.checkResultByFile("after$name.java")
    }
  }

  private suspend fun doTest() {
    val name = myFixture.getTestName(false)
    myFixture.configureByFile("before$name.java")

    withContext(Dispatchers.EDT) {
      GenerateLoggerHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)

      myFixture.checkResultByFile("after$name.java")
    }
  }
}