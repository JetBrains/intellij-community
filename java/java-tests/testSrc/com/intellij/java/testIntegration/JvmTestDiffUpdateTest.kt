// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testIntegration

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.actions.TestDiffContent
import com.intellij.execution.testframework.actions.TestDiffRequestProcessor
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.openapi.ListSelection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ArrayUtilRt
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil

abstract class JvmTestDiffUpdateTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("junit4", *ArrayUtilRt.toStringArray(JavaSdkUtil.getJUnit4JarPaths()))
    // The Jupiter assert takes the message as the last argument, so the fixtures need the Jupiter API too.
    moduleBuilder.addLibrary("junit6", *ArrayUtilRt.toStringArray(
      IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit6", "JUnit6").classesPaths))
  }

  private fun createDiffRequest(
    before: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String,
    /** Overrides the location URL, for a test whose class is not the file's top-level one. */
    location: String? = null,
  ): SimpleDiffRequest {
    myFixture.configureByText("$testClass.$fileExt", before)
    val root = SMRootTestProxy()
    val configuration = MockRuntimeConfiguration(project)
    root.testConsoleProperties = SMTRunnerConsoleProperties(configuration, "framework", DefaultRunExecutor())
    val testProxy = SMTestProxy(testName, false, location ?: "java:test://$testClass/$testName").apply {
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

  /**
   * Checks that the diff numbers its first line as [line] of the test file, before and after the accept of the diff.
   * The gutter converts the line numbers during paint, where it cannot take the read lock.
   */
  protected fun checkFirstLineNumber(
    before: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String,
    line: Int,
  ) {
    val request = createDiffRequest(before, testClass, testName, expected, actual, stackTrace, fileExt)
    val converter = request.contents.first().getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR)!!
    val lockAccesses = ContainerUtil.createConcurrentList<Throwable>()
    // Paint holds no lock. The thread of the test holds the write-intent lock, so we convert on a thread that holds no lock either.
    fun convertFirstLine(): Int = ApplicationManager.getApplication().executeOnPooledThread<Int> {
      ApplicationManagerEx.getApplicationEx().withLocksSoftlyProhibited<Int>(
        "The gutter paints the line numbers", { lockAccesses += it }) { converter.applyAsInt(0) }
    }.get()

    assertEquals(line, convertFirstLine())
    val document = getDiffDocument(request)
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable { document.replaceString(0, document.textLength, actual) })
    assertEquals(line, convertFirstLine())
    assertEmpty(lockAccesses)
  }

  protected open fun checkAcceptFullDiff(
    before: String,
    after: String,
    testClass: String,
    testName: String,
    expected: String,
    actual: String,
    stackTrace: String,
    fileExt: String,
    location: String? = null,
  ) = checkChangeDiff(before, after, testClass, testName, expected, actual, stackTrace, fileExt, location) { document ->
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
    location: String? = null,
    change: (Document) -> Unit
  ) {
    val request = createDiffRequest(before, testClass, testName, expected, actual, stackTrace, fileExt, location)
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