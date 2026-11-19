// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.debugger.mockJDI.MockVirtualMachine
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.util.function.BiConsumer

/**
 * Runs the DfaAssist checks of one test. Create it with [dfaAssistFixture].
 */
@TestOnly
class DfaAssistFixture internal constructor(private val codeInsight: LightJavaCodeInsightTestFixture) {
  /**
   * Configures a file [fileName] with [text], and computes the DfaAssist hints at the caret of [text].
   * [mockValues] fills the variables of the mock frame.
   * The test fails when the hints and the unreachable ranges do not match the comments of [text],
   * for example the `TRUE` and `unreachable_start` comments. [context] is the message of a failure.
   *
   * Call it off the EDT. The fixture configures the file on the EDT.
   */
  @JvmOverloads
  fun doTest(text: String, mockValues: BiConsumer<in MockVirtualMachine, in MockStackFrame>, fileName: String, context: String? = "") {
    runDfaAssistTest(codeInsight.project, codeInsight.testRootDisposable, text, mockValues, context) { filteredText ->
      codeInsight.configureByText(fileName, filteredText)
      findDfaAssistElementAtCaret(codeInsight.file, codeInsight.editor, context)
    }
  }
}

/**
 * Creates a [DfaAssistFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
 *
 * Declare [projectFixture] with [com.intellij.testFramework.fixtures.lightJavaProjectFixture] in a static field or a companion object.
 * Its descriptor gives the JDK and the libraries of the tests, for example
 * [com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK].
 */
@TestOnly
fun dfaAssistFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<DfaAssistFixture> = testFixture("dfaAssistFixture") {
  val codeInsight = lightJavaCodeInsightFixture(projectFixture).init()
  initialized(DfaAssistFixture(codeInsight)) {}
}
