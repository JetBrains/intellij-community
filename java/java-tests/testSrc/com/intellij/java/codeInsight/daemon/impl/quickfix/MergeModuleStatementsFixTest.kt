// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil.getJavaTestDataPath
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M6
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MergeModuleStatementsFixTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture, getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/mergeModuleStatementsFix")
  private val myFixture get() = java9.fixture

  @Test
  fun testExports1(): Unit = doTest("exports", "my.api")
  @Test
  fun testExports2(): Unit = doTest("exports", "my.api")
  @Test
  fun testExports3(): Unit = doTest("exports", "my.api")

  @Test
  fun testProvides1(): Unit = doTest("provides", "my.api.MyService")
  @Test
  fun testProvides2(): Unit = doTest("provides", "my.api.MyService")
  @Test
  fun testProvides3(): Unit = doTest("provides", "my.api.MyService")

  @Test
  fun testOpens1(): Unit = doTest("opens", "my.api")
  @Test
  fun testOpens2(): Unit = doTest("opens", "my.api")
  @Test
  fun testOpens3(): Unit = doTest("opens", "my.api")

  @BeforeEach
  fun setUp() {
    java9.addFile("module-info.java", "module M2 { }", M2)
    java9.addFile("module-info.java", "module M4 { }", M4)
    java9.addFile("module-info.java", "module M6 { }", M6)

    java9.addFile("my/api/MyService.java", "package my.api; public class MyService {}")
    java9.addFile("my/impl/MyServiceImpl.java", "package my.impl; public class MyServiceImpl extends my.api.MyService {}")
    java9.addFile("my/impl/MyServiceImpl1.java", "package my.impl; public class MyServiceImpl1 extends my.api.MyService {}")
    java9.addFile("my/impl/MyServiceImpl2.java", "package my.impl; public class MyServiceImpl2 extends my.api.MyService {}")
  }

  private fun doTest(type: String, name: String, expected: Boolean = true): Unit = timeoutRunBlocking {
    val testName = myFixture.getTestName(false)
    val virtualFile = myFixture.copyFileToProject("${testName}.java", "module-info.java")
    myFixture.configureFromExistingVirtualFile(virtualFile)

    myFixture.doHighlighting()
    withContext(Dispatchers.EDT) {
      val actions = LightQuickFixTestCase.getAvailableActions(myFixture.editor, myFixture.file)
      val actionText = QuickFixBundle.message("java.9.merge.module.statements.fix.name", type, name)
      val action = LightQuickFixTestCase.findActionWithText(actions, actionText)

      if (!expected) {
        assertNull(action, "Action \"$actionText\" is not expected")
        return@withContext
      }

      assertNotNull(action, "No action \"$actionText\" in ${actions.map { it.text }}")
      myFixture.launchAction(action)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      myFixture.checkResultByFile("${testName}_after.java")
    }
  }
}