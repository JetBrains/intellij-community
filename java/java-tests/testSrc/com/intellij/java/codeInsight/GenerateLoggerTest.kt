// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiClass
import com.intellij.refactoring.introduce.PsiIntroduceTarget
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.UiInterceptors
import com.intellij.ui.UiInterceptors.UiInterceptor
import com.intellij.ui.components.JBList
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import javax.swing.ListModel

class GenerateLoggerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateLogger"

  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest()
  }

  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest()
  }

  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest()
  }

  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest()
  }

  fun testAnonymousClass() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest()
  }

  fun testImplicitlyDeclaredClass() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    myFixture.configureByText("implicitlyDeclaredClass.java",
                              """
                                void main() {<caret>
                                }
                                """.trimIndent())

    val loggers = JvmLogger.findSuitableLoggers(module)
    TestCase.assertTrue(loggers.isNotEmpty())
    val element = file.findElementAt(editor.caretModel.offset)!!
    val places = JvmLogger.getPossiblePlacesForLogger(element, loggers)

    TestCase.assertTrue(places.isEmpty())
  }

  fun testNestedClassesOuterClass() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Outer"
    )
  }

  fun testNestedClassesNestedClass() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  fun testMultipleNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  fun testSaveSettings() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    assertEquals(project.service<JvmLoggingSettingsStorage>().state.loggerId, UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)
    doTest()
    assertEquals(project.service<JvmLoggingSettingsStorage>().state.loggerId, "Log4j")
  }

  fun testRespectCustomLoggerName() {
    val state = project.service<JvmLoggingSettingsStorage>().state
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

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_21

  private fun doTestWithMultiplePlaces(expectedClassNameList: List<String>, selectedClass: String) {
    val name = getTestName(false)
    myFixture.configureByFile("before$name.java")

    UiInterceptors.register(object : UiInterceptor<JBPopup>(JBPopup::class.java) {
      @Suppress("UNCHECKED_CAST")
      override fun doIntercept(popup: JBPopup) {
        Disposer.register(myFixture.testRootDisposable, popup)
        val content = UIUtil.findComponentOfType(popup.getContent(), JBList::class.java)

        TestCase.assertTrue(content?.selectedIndex == 0)

        val model: ListModel<PsiIntroduceTarget<PsiClass>> = content!!.model as ListModel<PsiIntroduceTarget<PsiClass>>

        val actualClassNameList: MutableList<String> = mutableListOf()

        (0..<model.size).forEach {
          actualClassNameList.add(model.getElementAt(it).render())
        }

        assertEquals(expectedClassNameList, actualClassNameList)

        val indexOfPreferredOption = actualClassNameList.indexOf(selectedClass)

        TestCase.assertTrue(indexOfPreferredOption >= 0)

        content.selectedIndex = indexOfPreferredOption

        popup.closeOk(null)
      }

    })

    GenerateLoggerHandler().invoke(project, editor, file)

    myFixture.checkResultByFile("after$name.java")
  }

  private fun doTest() {
    val name = getTestName(false)
    myFixture.configureByFile("before$name.java")

    GenerateLoggerHandler().invoke(project, editor, file)

    myFixture.checkResultByFile("after$name.java")
  }
}