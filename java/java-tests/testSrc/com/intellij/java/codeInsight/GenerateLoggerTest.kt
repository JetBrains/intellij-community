// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.GenerateLoggerHandler
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
import com.intellij.ui.logging.JavaSettingsStorage
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import javax.swing.ListModel

class GenerateLoggerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateLogger"

  fun testSlf4j() {
    myFixture.addClass("""
      package org.slf4j;
      
      interface Logger {}
    """.trimIndent())
    myFixture.addClass("""
      package org.slf4j;
      
      interface LoggerFactory{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTest()
  }

  fun testLog4j2() {
    myFixture.addClass("""
      package org.apache.logging.log4j;
      
      interface Logger {}
    """.trimIndent())
    myFixture.addClass("""
      package org.apache.logging.log4j;
      
      interface LogManager{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTest()
  }

  fun testLog4j() {
    myFixture.addClass("""
      package org.apache.log4j;
      
      interface Logger {
      static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTest()
  }

  fun testApacheCommons() {
    myFixture.addClass("""
    package org.apache.commons.logging;
    
    interface Log {
    }
  """.trimIndent())
    myFixture.addClass("""
    package org.apache.commons.logging;
    
    interface LogFactory {
      static Log getLog(Class<?> clazz) {}
    }
  """.trimIndent())
    doTest()
  }

  fun testNestedClassesOuterClass() {
    myFixture.addClass("""
      package org.slf4j;
      
      interface Logger {}
    """.trimIndent())
    myFixture.addClass("""
      package org.slf4j;
      
      interface LoggerFactory{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Outer"
    )
  }

  fun testNestedClassesNestedClass() {
    myFixture.addClass("""
      package org.slf4j;
      
      interface Logger {}
    """.trimIndent())
    myFixture.addClass("""
      package org.slf4j;
      
      interface LoggerFactory{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  fun testMultipleNestedClasses() {
    myFixture.addClass("""
      package org.slf4j;
      
      interface Logger {}
    """.trimIndent())
    myFixture.addClass("""
      package org.slf4j;
      
      interface LoggerFactory{
       static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    doTestWithMultiplePlaces(
      listOf(
        "class Outer",
        "class Inner",
      ),
      "class Inner"
    )
  }

  fun testSaveSettings() {
    myFixture.addClass("""
      package org.apache.log4j;
      
      interface Logger {
      static <T> Logger getLogger(Class<T> clazz) {}
      }
    """.trimIndent())
    assertEquals(project.service<JavaSettingsStorage>().state.loggerName, UnspecifiedLogger.UNSPECIFIED_LOGGER_NAME)
    doTest()
    assertEquals(project.service<JavaSettingsStorage>().state.loggerName, "Log4j")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST_WITH_LATEST_JDK

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