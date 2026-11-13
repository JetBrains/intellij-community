// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.JavaTestUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * The fixture of the Java resolve tests. Create it with [lightResolveFixture].
 *
 * [findReferenceAtCaret] configures a file of the test data and finds the reference at the caret.
 * A test that reads the PSI of the returned reference needs its own read action.
 */
@TestOnly
class LightResolveFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Configures [filePath] of the test data, and returns the reference at the caret or `null`.
   */
  fun findReferenceAtCaret(filePath: String): PsiReference? {
    val file = fixture.configureByFile(filePath)
    return ReadAction.computeBlocking<PsiReference?, RuntimeException> { file.findReferenceAt(fixture.caretOffset) }
  }
}

/**
 * Creates a [LightResolveFixture] for one test. Declare it as an instance field.
 *
 * The test data path is [testDataPath], or `psi/resolve/` of the Java test data when it is `null`.
 */
@TestOnly
@JvmOverloads
fun lightResolveFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<LightResolveFixture> = testFixture("lightResolveFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = testDataPath ?: (JavaTestUtil.getJavaTestDataPath() + "/psi/resolve/")
  initialized(LightResolveFixture(fixture)) {}
}
