/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.JavaTestUtil.getRelativeJavaTestDataPath
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*

/**
 * @author Pavel.Dolgov
 */
class MergeModuleStatementsFixTest : LightJava9ModulesCodeInsightFixtureTestCase() {

  override fun getBasePath() = getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/mergeModuleStatementsFix"

  fun testExports1() = doTest("exports", "my.api")
  fun testExports2() = doTest("exports", "my.api")

  fun testProvides1() = doTest("provides", "my.api.MyService")
  fun testProvides2() = doTest("provides", "my.api.MyService")
  fun testProvides3() = doTest("provides", "my.api.MyService")

  fun testOpens1() = doTest("opens", "my.api")
  fun testOpens2() = doTest("opens", "my.api")


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

  private fun doTest(type: String, name: String) {
    val testName = getTestName(false)
    val virtualFile = myFixture.copyFileToProject("${testName}.java", "module-info.java")
    myFixture.configureFromExistingVirtualFile(virtualFile)

    val action = findActionWithText(QuickFixBundle.message("java.9.merge.module.statements.fix.name", type, name))
    myFixture.launchAction(action)
    myFixture.checkResultByFile("${testName}_after.java")
  }

  private fun findActionWithText(actionText: String): IntentionAction {
    myFixture.doHighlighting()

    val actions = LightQuickFixTestCase.getAvailableActions(editor, file)
    val action = LightQuickFixTestCase.findActionWithText(actions, actionText)
    assertNotNull("No action [$actionText] in ${actions.map { it.text }}", action)
    return action
  }
}