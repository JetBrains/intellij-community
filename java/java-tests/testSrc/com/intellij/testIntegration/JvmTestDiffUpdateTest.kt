// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.actions.TestDiffContent
import com.intellij.execution.testframework.actions.TestDiffRequestProcessor
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.openapi.ListSelection
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ArrayUtilRt
import com.intellij.util.asSafely

abstract class JvmTestDiffUpdateTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("junit4", *ArrayUtilRt.toStringArray(JavaSdkUtil.getJUnit4JarPaths()))
  }

  private fun createDiffRequest(
    before: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String
  ): SimpleDiffRequest {
    myFixture.configureByText("$testClass.$fileExt", before)
    val root = SMRootTestProxy()
    val configuration = MockRuntimeConfiguration(project)
    root.testConsoleProperties = SMTRunnerConsoleProperties(configuration, "framework", DefaultRunExecutor())
    val testProxy = SMTestProxy(testName, false, "java:test://$testClass/$testName").apply {
      locator = JavaTestLocator.INSTANCE
      setTestFailed("fail", stackTrace, true)
    }
    root.addChild(testProxy)
    val hyperlink = testProxy.createHyperlink(expected, actual, null, null, true)
    val requestProducer = TestDiffRequestProcessor.createRequestChain(
      myFixture.project, ListSelection.createSingleton(hyperlink)
    ).requests.first()
    val diff = requestProducer.process(UserDataHolderBase(), EmptyProgressIndicator()) as SimpleDiffRequest
    diff.onAssigned(true)
    return diff
  }

  private fun getDiffDocument(request: SimpleDiffRequest) = request.contents.firstOrNull().asSafely<DocumentContent>()?.document?.apply {
    setReadOnly(false)
  }!!

  protected open fun checkHasNoDiff(
    before: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String
  ) {
    val request = createDiffRequest(before, testClass, testName, expected, actual, stackTrace, fileExt)
    assertNull(request.contents.firstOrNull { it is TestDiffContent })
  }

  protected open fun checkAcceptFullDiff(
    before: String,
    after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String
  ) = checkChangeDiff(before, after, testClass, testName, expected, actual, stackTrace, fileExt) { document ->
    document.replaceString(0, document.textLength, actual)
  }

  protected open fun checkChangeDiff(
    before: String,
    after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String,
    change: (Document) -> Unit
  ) {
    val request = createDiffRequest(before, testClass, testName, expected, actual, stackTrace, fileExt)
    val document = getDiffDocument(request)
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable { change(document) })
    assertEquals(after, myFixture.file.text)
  }

  protected open fun checkPhysicalDiff(
    before: String,
    after: String,
    diffAfter: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String,
    change: (Document) -> Unit
  ) {
    val request = createDiffRequest(before, testClass, testName, expected, actual, stackTrace, fileExt)
    val physDocument = PsiDocumentManager.getInstance(project).getDocument(myFixture.file)!!
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable { change(physDocument) })
    val diffDocument = getDiffDocument(request)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals(after, myFixture.file.text)
    assertEquals(diffAfter, diffDocument.text)
  }
}