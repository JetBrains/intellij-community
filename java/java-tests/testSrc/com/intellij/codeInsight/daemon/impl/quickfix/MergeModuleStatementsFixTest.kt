// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil.getRelativeJavaTestDataPath
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl

class MergeModuleStatementsFixTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  override fun getBasePath(): String = getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/mergeModuleStatementsFix"

  fun testExports1(): Unit = doTest("exports", "my.api")
  fun testExports2(): Unit = doTest("exports", "my.api")
  fun testExports3(): Unit = doTest("exports", "my.api")

  fun testProvides1(): Unit = doTest("provides", "my.api.MyService")
  fun testProvides2(): Unit = doTest("provides", "my.api.MyService")
  fun testProvides3(): Unit = doTest("provides", "my.api.MyService")

  fun testOpens1(): Unit = doTest("opens", "my.api")
  fun testOpens2(): Unit = doTest("opens", "my.api")
  fun testOpens3(): Unit = doTest("opens", "my.api")


  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { }", M2)
    addFile("module-info.java", "module M4 { }", M4)
    addFile("module-info.java", "module M6 { }", M6)

    addFile("my/api/MyService.java", "package my.api; public class MyService {}")
    addFile("my/impl/MyServiceImpl.java", "package my.impl; public class MyServiceImpl extends my.api.MyService {}")
    addFile("my/impl/MyServiceImpl1.java", "package my.impl; public class MyServiceImpl1 extends my.api.MyService {}")
    addFile("my/impl/MyServiceImpl2.java", "package my.impl; public class MyServiceImpl2 extends my.api.MyService {}")
  }

  private fun doTest(type: String, name: String, expected: Boolean = true) {
    val testName = getTestName(false)
    val virtualFile = myFixture.copyFileToProject("${testName}.java", "module-info.java")
    myFixture.configureFromExistingVirtualFile(virtualFile)

    myFixture.doHighlighting()
    val actions = LightQuickFixTestCase.getAvailableActions(editor, file)
    val actionText = QuickFixBundle.message("java.9.merge.module.statements.fix.name", type, name)
    val action = LightQuickFixTestCase.findActionWithText(actions, actionText)

    if (!expected) {
      assertNull("Action \"$actionText\" is not expected", action)
      return
    }

    assertNotNull("No action \"$actionText\" in ${actions.map { it.text }}", action)
    myFixture.launchAction(action)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResultByFile("${testName}_after.java")
  }
}